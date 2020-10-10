package io.immutables.regres;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.io.Resources;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.TypeToken;
import io.immutables.Nullable;
import io.immutables.Source;
import io.immutables.Source.Position;
import io.immutables.codec.Codec;
import io.immutables.codec.Codec.ContainerCodec;
import io.immutables.codec.Codec.FieldIndex;
import io.immutables.codec.Codecs;
import io.immutables.codec.Resolver;
import io.immutables.collect.Vect;
import io.immutables.regres.Coding.StatementParameterOut;
import io.immutables.regres.SqlAccessor.Batch;
import io.immutables.regres.SqlAccessor.Column;
import io.immutables.regres.SqlAccessor.Named;
import io.immutables.regres.SqlAccessor.Single;
import io.immutables.regres.SqlAccessor.Spread;
import io.immutables.regres.SqlAccessor.UpdateCount;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.immutables.value.Value.Derived;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Minimalistic toolkit for SQL access which is based on reflection proxies of interfaces defining data access methods
 * and underlying SQL code snippets (Dao.java + Dao.sql in classpath). In the SQL file {@code --.<methodName>} comments
 * are used to lead sections of SQL which corresponds to specific methods in Java interface.
 */
// TODO Transactions/Control handle/with lambda
// TODO Exception improvements/consistency
// TODO GetGeneratedKeys (maybe someday)
@Enclosing
public final class Regresql {
  private Regresql() {}

  @Immutable
  interface MethodSnippet {
    String name();
    Vect<String> placeholders();
    Source.Range identifierRange();
    Source.Range statementsRange();
    String preparedStatements();

    class Builder extends ImmutableRegresql.MethodSnippet.Builder {}
  }

  @Immutable
  interface SqlSource {
    String filename();
    CharSequence content();
    Source.Lines lines();

    default Position get(int position) {
      return lines().get(position);
    }

    default Source.Problem problemAt(Source.Range range, String message, String hint) {
      return new Source.Problem(filename(), content(), lines(), range, message, hint);
    }

    class Builder extends ImmutableRegresql.SqlSource.Builder {}
  }

  @Immutable
  interface MethodProfile {
    String name();
    OptionalInt batchParameter();
    default boolean useBatching() {
      return batchParameter().isPresent();
    }

    boolean returnUpdateCount();

    public boolean extractColumn();

    default boolean returnResultSet() {
      return !returnUpdateCount();
    }

    Vect<ParameterProfile> parameters();

    Optional<Codec<Object>> returnTypeCodec();

    Type returnType();

    @Derived
    default FieldIndex parameterIndex() {
      return Codec.knownFields(parameters().stream()
          .map(ParameterProfile::name)
          .toArray(String[]::new));
    }

    @Derived
    default Map<String, ParameterProfile> parametersByName() {
      return Maps.uniqueIndex(parameters(), ParameterProfile::name);
    }
    // future
    // boolean returnGeneratedKeys();
    class Builder extends ImmutableRegresql.MethodProfile.Builder {}
  }

  @Immutable
  interface ParameterProfile {
    String name();
    boolean batch();
    Optional<String> spread();
    Codec<Object> codec();
    TypeToken<?> type();

    class Builder extends ImmutableRegresql.ParameterProfile.Builder {}
  }

  @SuppressWarnings("unchecked") // cast guaranteed by Proxy contract, runtime verified
  public static <T> T create(Class<T> accessor, Resolver codecs, ConnectionProvider connections) {
    checkArgument(accessor.isInterface() && accessor.getCanonicalName() != null,
        "%s is not valid SQL access interface", accessor);

    return (T) Proxy.newProxyInstance(
        accessor.getClassLoader(),
        new Class<?>[]{accessor},
        handlerFor(accessor, codecs, connections));
  }

  private static ImmutableMap<String, MethodProfile> compileProfiles(
      Class<?> accessor,
      Set<String> methods,
      Resolver codecs) {
    ImmutableMap.Builder<String, MethodProfile> builder = ImmutableMap.builder();

    for (Method m : accessor.getMethods()) {
      String name = m.getName();
      if (methods.contains(name)) {
        builder.put(name, profileMethod(m, codecs));
      }
      // ignore here everything else, either assume these kind of mismatches handled elsewhere
      // or just wish it will be ok.
    }
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private static MethodProfile profileMethod(Method method, Resolver codecs) {
    MethodProfile.Builder builder = new MethodProfile.Builder();

    @Nullable UpdateCount updateCount = method.getAnnotation(UpdateCount.class);
    @Nullable Column column = method.getAnnotation(Column.class);
    @Nullable Single single = method.getAnnotation(Single.class);

    Type returnType = method.getGenericReturnType();

    if (updateCount != null && (column != null || single != null)) throw new IllegalStateException(
        "@UpdateCount and (@Column extraction or @Single result) cannot be used together on " + method);

    if (updateCount != null
        && returnType != int.class
        && returnType != int[].class
        && returnType != long.class
        && returnType != long[].class) {
      throw new IllegalStateException(
          "@UpdateCount requires return type int, int[], long, or long[] on " + method);
    }

    Vect<ParameterProfile> parameters = profileParameters(method, codecs);

    builder.addAllParameters(parameters);

    boolean useBatch = false;
    for (int i = 0; i < parameters.size(); i++) {
      if (parameters.get(i).batch()) {
        builder.batchParameter(i);
        useBatch = true;
        break;
      }
    }

    boolean returnUpdateCount = updateCount != null || returnType == void.class;

    if (useBatch && !returnUpdateCount) {
      throw new IllegalStateException(
          "@Batch requires returning @UpdateCount or void return type" + method);
    }

    if (!returnUpdateCount) {
      TypeToken<Object> t = (TypeToken<Object>) TypeToken.of(returnType);
      Codec<Object> c = codecs.get(t, Codecs.findQualifier(method));

      if (column != null) {
        if (single != null) {
          c = new Coding.ColumnExtractor(c, column);
        } else if (c instanceof ContainerCodec<?, ?>) {
          ContainerCodec<Object, Object> cc = (ContainerCodec<Object, Object>) c;
          c = cc.withElement(new Coding.ColumnExtractor(cc.element(), column));
        } else throw new IllegalStateException(
            "@Column can only be used with @Single for " + method
                + ". The codec for " + t + " is not known to support such extraction (a ContainerCodec can)");
      }

      if (single != null) {
        c = new Coding.SingleRowDecoder(c, single);
      }

      builder.returnTypeCodec(c);
    }

    builder.name(method.getName());
    builder.returnType(returnType);
    builder.returnUpdateCount(returnUpdateCount);
    builder.extractColumn(column != null);

    return builder.build();
  }

  private static final TypeVariable<?> ITERABLE_ELEMENT = Iterable.class.getTypeParameters()[0];

  @SuppressWarnings("unchecked")
  private static Vect<ParameterProfile> profileParameters(Method m, Resolver codecs) {
    Vect.Builder<ParameterProfile> profiles = Vect.builder();
    Type[] types = m.getGenericParameterTypes();

    int batchCount = 0;
    Parameter[] parameters = m.getParameters();

    for (int i = 0; i < parameters.length; i++) {
      Parameter p = parameters[i];

      @Nullable Named named = p.getAnnotation(Named.class);
      @Nullable Batch batch = p.getAnnotation(Batch.class);
      @Nullable Spread spread = p.getAnnotation(Spread.class);

      if (spread == null && named == null) throw new IllegalArgumentException(
          "Parameter #" + i + " of "
              + m.getDeclaringClass() + "." + m.getName()
              + " must have @Named annotation. (unless @Spread)");

      TypeToken<?> type = TypeToken.of(types[i]);

      if (batch != null) {
        batchCount++;

        Class<?> raw = type.getRawType();
        if (raw.isArray()) {
          type = TypeToken.of(raw.getComponentType());
        } else if (Iterable.class.isAssignableFrom(raw)) {
          type = type.resolveType(ITERABLE_ELEMENT);
        } else throw new IllegalStateException(
            "@Batch parameter must an Iterable or an array, but was " + type);
      }

      Codec<Object> codec = codecs.get((TypeToken<Object>) type, Codecs.findQualifier(p));

      profiles.add(new ParameterProfile.Builder()
          .name(named != null ? named.value() : p.getName())
          .batch(batch != null)
          .spread(Optional.ofNullable(spread).map(Spread::prefix))
          .codec(codec)
          .type(type)
          .build());
    }

    if (batchCount > 1) throw new IllegalStateException(
        "@Batch should not be present on more than one parameter on " + m);

    return profiles.build();
  }

  static InvocationHandler handlerFor(Class<?> accessor, Resolver resolve, ConnectionProvider provider) {
    Set<String> methods = uniqueAccessMethods(accessor);
    Resolver codecs = Coding.wrap(resolve);

    // We allow "empty" interfaces as a way to access SQL (via getConnectionProvider() etc
    // and don't load/require SQL source in this case

    @Nullable SqlSource source;
    ImmutableMap<String, MethodSnippet> snippets;
    ImmutableMap<String, MethodProfile> profiles;
    if (!methods.isEmpty()) {
      source = loadSqlSource(accessor);
      snippets = parseSnippets(source, methods);
      profiles = compileProfiles(accessor, methods, codecs);
    } else {
      source = null;
      snippets = ImmutableMap.of();
      profiles = ImmutableMap.of();
    }

    return new AbstractInvocationHandler() {
      @Override
      protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if (isConnectionHandleMethod(method)) return provider.handle();

        MethodSnippet snippet = snippets.get(name);
        MethodProfile profile = profiles.get(name);

        if (profile == null || snippet == null) {
          throw new AssertionError("SQL method not defined: " + name);
        }

        try (ConnectionProvider.Handle handle = provider.handle();
            PreparedStatement statement = handle.connection.prepareStatement(snippet.preparedStatements())) {
          prepareStatement(statement, profile, snippet, args);
          return executeStatement(statement, profile);
        } catch (SQLException sqlException) {
          throw Errors.refineException(source, method, snippet, sqlException);
        }
      }
    };
  }

  private static boolean isConnectionHandleMethod(Method method) {
    return SqlAccessor.class.isAssignableFrom(method.getDeclaringClass())
        && method.getName().equals("connectionHandle")
        && method.getParameterCount() == 0;
  }

  private static void prepareStatement(
      PreparedStatement statement,
      MethodProfile profile,
      MethodSnippet snippet,
      Object[] args) throws SQLException, IOException {

    Vect<ParameterProfile> parameters = profile.parameters();
    StatementParameterOut out = new Coding.StatementParameterOut(profile.parameterIndex());

    if (profile.useBatching()) {
      int batchIndex = profile.batchParameter().orElseThrow(AssertionError::new);
      for (int i = 0; i < parameters.size(); i++) {
        if (i != batchIndex) {
          putArgument(out, parameters.get(i), i, args[i]);
        }
      }
      ParameterProfile batcher = parameters.get(batchIndex);
      Object batch = args[batchIndex];
      if (batch instanceof Iterable<?>) {
        for (Object o : (Iterable<?>) batch) {
          putArgument(out, batcher, batchIndex, o);
          fillStatement(statement, snippet.placeholders(), out);
          statement.addBatch();
        }
      } else {
        assert batch.getClass().isArray();
        int length = Array.getLength(batch);
        for (int i = 0; i < length; i++) {
          Object o = Array.get(batch, i);
          putArgument(out, batcher, batchIndex, o);
          fillStatement(statement, snippet.placeholders(), out);
          statement.addBatch();
        }
      }
    } else {
      for (int i = 0; i < parameters.size(); i++) {
        putArgument(out, parameters.get(i), i, args[i]);
      }
      fillStatement(statement, snippet.placeholders(), out);
    }
  }

  private static void putArgument(StatementParameterOut out, ParameterProfile p, int index, Object value)
      throws IOException {
    out.putField(index);
    if (p.spread().isPresent()) {
      out.spread(p.spread().get());
    }
    p.codec().encode(out, value);
  }

  private static void fillStatement(
      PreparedStatement statement,
      Vect<String> placeholders,
      StatementParameterOut out) throws SQLException, IOException {
    int i = 1;
    for (String p : placeholders) {
      Object value = out.get(p);
      if (value instanceof Instant) {
        // Experimental, only for instant for now
        statement.setTimestamp(i, new Timestamp(((Instant) value).toEpochMilli()));
      } else {
        statement.setObject(i, value);
      }
      i++;
    }
  }

  private static SqlSource loadSqlSource(Class<?> accessorInterface) throws AssertionError {
    String filename = resourceFilenameFor(accessorInterface);
    URL resource = accessorInterface.getResource(filename);

    if (resource == null) throw new MissingResourceException(
        filename + " must be present in classpath",
        accessorInterface.getCanonicalName(),
        filename);

    // We minimize any copying unless absolutely necessary.
    // Basically, we only have full source in a single Buffer which we fill in
    // only once and then only operate on its subsequences
    // (shallow copies which share character content)
    // Then we use StringBuffer (one per method) when using regex
    // to generate prepared statement strings which we then store
    // and use directly for JDBC. Also we collect placeholder strings in lists
    // per method.
    Source.Buffer content = new Source.Buffer();

    try {
      Resources.asCharSource(resource, StandardCharsets.UTF_8).copyTo(content);
    } catch (IOException readingClasspathResourceFailed) {
      throw new UncheckedIOException("Cannot read " + filename, readingClasspathResourceFailed);
    }

    return new SqlSource.Builder()
        .content(content)
        .filename(filename)
        .lines(Source.Lines.from(content))
        .build();
  }

  private static ImmutableMap<String, MethodSnippet> parseSnippets(SqlSource source, Set<String> methods) {
    Vect<MethodSnippet> snippets = parse(source.content(), source.lines());
    ImmutableListMultimap<String, MethodSnippet> byName = Multimaps.index(snippets, m -> m.name());

    List<Source.Problem> problems = new ArrayList<>();

    for (Entry<String, Collection<MethodSnippet>> e : byName.asMap().entrySet()) {
      String name = e.getKey();
      if (name.isEmpty()) {
        for (MethodSnippet nameless : e.getValue()) {
          problems.add(source.problemAt(
              nameless.statementsRange(),
              "SQL statements not under method",
              "Put statements after --.method declarations"));
        }
      } else if (!methods.contains(name)) {
        for (MethodSnippet unmatched : e.getValue()) {
          problems.add(source.problemAt(
              unmatched.identifierRange(),
              "There are no corresponding `" + name + "` method in interface",
              "Declared interface methods: " + String.join(", ", methods)));
        }
      } else if (e.getValue().size() > 1) {
        for (MethodSnippet duplicate : Vect.from(e.getValue()).rangeFrom(1)) {
          problems.add(source.problemAt(
              duplicate.identifierRange(),
              "Duplicate `" + name + "` declaration",
              "No method duplicates or overloads are allowed"));
        }
      }
    }

    SetView<String> missingSnippets = Sets.difference(methods, byName.keySet());
    if (!missingSnippets.isEmpty()) {
      for (String name : missingSnippets) {
        problems.add(source.problemAt(Source.Range.of(source.get(0)),
            "Missing `" + name + "` declaration",
            "Method declared in interface but has no SQL"));
      }
    }

    if (!problems.isEmpty()) throw new RuntimeException(
        "\n" + Joiner.on("\n").join(problems));

    return Maps.uniqueIndex(snippets, m -> m.name());
  }

  private static Set<String> uniqueAccessMethods(Class<?> accessorInterface) {
    Multiset<String> possiblyDuplicateMethods = Arrays.stream(accessorInterface.getMethods())
        .filter(Regresql::elibibleAccessMethod)
        .map(m -> m.getName())
        .collect(Collectors.toCollection(HashMultiset::create));

    Set<String> unique = ImmutableSet.copyOf(possiblyDuplicateMethods.elementSet());
    Multisets.removeOccurrences(possiblyDuplicateMethods, unique);

    checkArgument(possiblyDuplicateMethods.isEmpty(),
        "Method overloads are not supported for %s: %s", accessorInterface, possiblyDuplicateMethods);

    return unique;
  }

  private static boolean elibibleAccessMethod(Method m) {
    return Modifier.isAbstract(m.getModifiers())
        && !isConnectionHandleMethod(m);
  }

  private static String resourceFilenameFor(Class<?> accessorInterface) {
    String canonicalName = accessorInterface.getCanonicalName();
    assert canonicalName != null : "precondition checked before";
    // not sure if null package can be for unnamed package, handling just in case
    Package packageObject = accessorInterface.getPackage();
    String packageName = packageObject != null ? packageObject.getName() : "";
    String packagePath = packageName.replace('.', '/');
    String resourceFilename;
    if (canonicalName.startsWith(packageName + ".")) {
      resourceFilename = packagePath + "/" + canonicalName.substring(packageName.length() + 1);
    } else { // may include case for unnamed packages etc
      resourceFilename = canonicalName;
    }
    return "/" + resourceFilename + ".sql";
  }

  private static Vect<MethodSnippet> parse(CharSequence content, Source.Lines lines) {
    Vect.Builder<MethodSnippet> allMethods = Vect.builder();

    class Parser {
      @Nullable
      MethodSnippet.Builder openBuilder;
      @Nullable
      Source.Range openRange;

      void parse() {
        for (int i = 1; i <= lines.count(); i++) {
          Source.Range range = lines.getLineRange(i);
          CharSequence line = range.get(content);
          String name = methodName(line);

          if (!name.isEmpty()) {
            // method identifier line
            // flush any open method and start
            // new method builder
            flushMethod(content, range);
            openMethod(name, range);
          } else {
            // regular statement line
            if (openBuilder != null) {
              // begin or expand range for open method
              openRange = openRange == null ? range : openRange.span(range);
            } else {
              // can collect unnamed leading lines for error reporting
              openMethod("", range);
            }
          }
        }

        Source.Range initialEmptyRange = Source.Range.of(Source.Position.of(0, 1, 1));
        flushMethod(content, initialEmptyRange);
      }

      String methodName(CharSequence line) {
        if (line.length() > 3
            && line.charAt(0) == '-'
            && line.charAt(1) == '-'
            && line.charAt(2) == '.') {
          // can return empty string which is no method declared on this line
          // threat it as just an SQL comment. Or can be illegal name
          // anyway we expect these to be matched by the data access interface
          // method names and any descrepancy returned as errors
          return line.subSequence(3, line.length()).toString().trim();
        }
        return ""; // none
      }

      void openMethod(String name, Source.Range range) {
        openRange = null;
        openBuilder = new MethodSnippet.Builder()
            .identifierRange(range)
            .name(name);
      }

      void flushMethod(CharSequence content, Source.Range currentRange) {
        if (openBuilder != null) {
          if (openRange != null) {
            prepareRange(content);
            allMethods.add(openBuilder.build());
          } else {
            prepareEmpty(currentRange);
            allMethods.add(openBuilder.build());
          }
        }
      }

      /**
       * incomplete / empty method, defer error to runtime (like empty SQL statement)
       * otherwise it would too painful during development
       */
      void prepareEmpty(Source.Range currentRange) {
        openBuilder.statementsRange(Source.Range.of(currentRange.begin))
            .preparedStatements("--");
      }

      /**
       * Parses source to extract placeholder list and also
       * builds prepared statement where placeholders are substituted
       * with '?' character to match the JDBC prepared statement syntax.
       */
      void prepareRange(CharSequence content) {
        openBuilder.statementsRange(openRange);

        CharSequence source = content.subSequence(
            openRange.begin.position,
            openRange.end.position);

        // regex API works with old buffers
        StringBuffer buffer = new StringBuffer();
        Matcher matcher = PLACEHOLDER.matcher(source);
        while (matcher.find()) {
          if (source.charAt(matcher.start(0) + 1) == ':') {
            // first char is always ':' in this match, so
            // we look for the second char and see if we
            // ignore and append the same SQL verbatim, consider this
            // just type coercion expression.
            // could be potentially "fixed" by just not matching
            // such sequences in the first place, don't know how
            matcher.appendReplacement(buffer, "$0");
          } else {
            String placeholder = matcher.group(1);
            openBuilder.addPlaceholders(placeholder);
            matcher.appendReplacement(buffer, "?");
            // append number of spaces to match the length of original
            // placeholder so SQL syntax error reporting would operate
            // on the same source positions/offsets as template definitions.
            for (int i = 0; i < placeholder.length(); i++) {
              buffer.append(' ');
            }
          }
        }
        matcher.appendTail(buffer);
        openBuilder.preparedStatements(buffer.toString());
      }
    }

    new Parser().parse();

    return allMethods.build();
  }

  static Object executeStatement(PreparedStatement statement, MethodProfile profile)
      throws SQLException, IOException {

    Type returnType = profile.returnType();
    boolean useUpdateCount = profile.returnUpdateCount();
    boolean voidUpdateCount = useUpdateCount && returnType == void.class;
    boolean largeUpdateCount = useUpdateCount && (returnType == long.class || returnType == long[].class);
    boolean sumUpdateCount = useUpdateCount && (returnType == int.class || returnType == long.class);

    // While I usually use early returns, here, for the sake of symmetry and clarity
    // (yep seems to be clearer in this case) we use return value and if/else branches.
    // do not initialize, forcing compiler to check branches
    Object returnValue;

    if (profile.useBatching()) {
      if (largeUpdateCount) { // large update count
        long[] updates = statement.executeLargeBatch();

        if (sumUpdateCount) {
          returnValue = Arrays.stream(updates).sum();
        } else {
          returnValue = updates;
        }
      } else { // long update count and void
        int[] updates = statement.executeBatch();

        if (voidUpdateCount) {
          returnValue = null;
        } else if (sumUpdateCount) {
          returnValue = Arrays.stream(updates).sum();
        } else {
          returnValue = updates;
        }
      }
    } else { // not batching
      boolean hasResultSet = statement.execute();

      if (useUpdateCount) {
        if (voidUpdateCount) {
          returnValue = null;
        } else if (largeUpdateCount) { // long update count
          List<Long> updates = new ArrayList<>();
          for (long count; ; ) {
            count = statement.getLargeUpdateCount();
            if (count >= 0) {
              updates.add(count);
            }
            if (!hasResultSet && count < 0) break;
            hasResultSet = statement.getMoreResults();
          }
          if (sumUpdateCount) {
            returnValue = updates.stream().mapToLong(l -> l).sum();
          } else {
            returnValue = updates.stream().mapToLong(l -> l).toArray();
          }
        } else { // int update count
          List<Integer> updates = new ArrayList<>();
          for (int count; ; ) {
            count = statement.getUpdateCount();
            if (count >= 0) {
              updates.add(count);
            }
            if (!hasResultSet && count < 0) break;
            hasResultSet = statement.getMoreResults();
          }
          if (sumUpdateCount) {
            returnValue = updates.stream().mapToInt(l -> l).sum();
          } else {
            returnValue = updates.stream().mapToInt(l -> l).toArray();
          }
        }
      } else { // reading result set (not update count)
        Codec<Object> codec = profile.returnTypeCodec().orElseThrow(AssertionError::new);

        boolean wasResultSet = false;

        returnValue = null; // cannot use clean branching below, have to initialize early

        if (hasResultSet) {
          Coding.ResultSetIn in = new Coding.ResultSetIn(statement.getResultSet());
          returnValue = codec.decode(in);
          wasResultSet = true;
        }

        for (int count; ; ) {
          count = statement.getUpdateCount();
          if (!hasResultSet && count < 0) break;
          hasResultSet = statement.getMoreResults();

          if (hasResultSet) {
            if (wasResultSet) throw new IllegalStateException(
                "Only single result set can be processes, Use sql UNION ALL to merge multiple results");

            Coding.ResultSetIn in = new Coding.ResultSetIn(statement.getResultSet());
            returnValue = codec.decode(in);
            wasResultSet = true;
          }
        }

        if (!wasResultSet) throw new IllegalStateException(
            "ResultSet exected but there was none. Fix SQL query, or use void return type or int/long @UpdateCount");
      } // end resultsets
    } // end not batch

    return returnValue;
  }

  /*
    private static class UncheckedSqlException extends RuntimeException {
      UncheckedSqlException(SQLException ex) {
        super(ex);
      }
      UncheckedSqlException(IOException ex) {
        super(ex);
      }
    }
  */
  private static final Pattern PLACEHOLDER = Pattern.compile("[:]{1,2}([a-zA-Z0-9.]+)");
}
