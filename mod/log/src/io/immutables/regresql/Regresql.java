package io.immutables.regresql;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.io.Resources;
import io.immutables.Nullable;
import io.immutables.Source;
import io.immutables.Source.Position;
import io.immutables.collect.Vect;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.immutables.value.Value.Immutable;
import static com.google.common.base.Preconditions.checkArgument;

public final class Regresql {
	private Regresql() {}

	private static final Pattern PLACEHOLDER = Pattern.compile("[:]{1,2}([a-zA-Z0-9.]+)");

	@Immutable
	interface MethodSnippet {
		String name();
		List<String> placeholders();
		Source.Range identifierRange();
		Source.Range statementsRange();
		String preparedStatements();

		class Builder extends ImmutableMethodSnippet.Builder {}
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

		class Builder extends ImmutableSqlSource.Builder {}
	}
	
	@Immutable
	interface InOutProfile {
		
		default boolean useBatch() {
			return false;
		}
		
		default boolean useUpdateCount() {
			return false;
		}
		
		default void set(PreparedStatement statement, int parameterIndex, String placeholder, int batchIndex) {
			
		}
		
		default void resultSet() {
			
		}
	}

	@SuppressWarnings("unchecked") // cast guaranteed by Proxy contract, runtime verified
	public static <T> T load(Class<T> accessor) {
		checkArgument(accessor.isInterface()
				&& accessor.getCanonicalName() != null,
				"%s is not valid SQL access interface", accessor);

		return (T) Proxy.newProxyInstance(
				accessor.getClassLoader(),
				new Class<?>[] {accessor},
				handlerFor(accessor));
	}
	
	private static ImmutableMap<String, InOutProfile> compileProfiles(Class<?> accessor, Set<String> methods) {
		return null; //TODO auto
	}

	static InvocationHandler handlerFor(Class<?> accessor) {
		SqlSource source = loadSqlSource(accessor);
		Set<String> methods = uniqueAccessMethods(accessor);
		ImmutableMap<String, MethodSnippet> snippets = parseSnippets(source, methods);
		ImmutableMap<String, InOutProfile> profiles = compileProfiles(accessor, methods);

		return new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				MethodSnippet snippet = snippets.get(method.getName());

				try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres");
						PreparedStatement statement = connection.prepareStatement(snippet.preparedStatements())) {
					List<String> placeholders = snippet.placeholders();
					int i = 0;
					for (String p : placeholders) {
						statement.setInt(++i, i);
					}
					return statement.executeUpdate();
				} catch (SQLException sqlException) {
					throw ErrorRefining.refineException(source, method, snippet, sqlException);
				}
			}
		};
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

	private static ImmutableMap<String, MethodSnippet> parseSnippets(
			SqlSource source,
			Set<String> methods) {
		ImmutableList<MethodSnippet> snippets = parse(source.content(), source.lines());
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

		if (!problems.isEmpty()) throw new RuntimeException(
				"\n" + Joiner.on("\n").join(problems));

		return Maps.uniqueIndex(snippets, m -> m.name());
	}

	private static Set<String> uniqueAccessMethods(Class<?> accessorInterface) {
		Multiset<String> possiblyDuplicateMethods = Arrays.stream(accessorInterface.getMethods())
				.filter(m -> Modifier.isAbstract(m.getModifiers()))
				.map(m -> m.getName())
				.collect(Collectors.toCollection(HashMultiset::create));

		Set<String> unique = ImmutableSet.copyOf(possiblyDuplicateMethods.elementSet());
		Multisets.removeOccurrences(possiblyDuplicateMethods, unique);

		checkArgument(possiblyDuplicateMethods.isEmpty(),
				"Method overloads are not supported for %s: %s", accessorInterface, possiblyDuplicateMethods);

		return unique;
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

	private static ImmutableList<MethodSnippet> parse(CharSequence content, Source.Lines lines) {
		ImmutableList.Builder<MethodSnippet> allMethods = ImmutableList.builder();

		class Parser {
			@Nullable
			MethodSnippet.Builder openBuilder = null;
			@Nullable
			Source.Range openRange = null;

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

	public static void main(String... args) throws Exception {
		Stopwatch sw = Stopwatch.createStarted();
		try {
			Sample sample = load(Sample.class);
			System.out.println(sw);
			sample.method1();
		} finally {
			System.out.println(sw);
		}
	}
}
