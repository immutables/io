package io.immutables.micro;

import java.net.URI;
import java.util.Collection;
import javax.inject.Inject;
import javax.inject.Provider;
import com.google.inject.Key;
import org.immutables.data.Data;
import org.immutables.value.Value;
import static com.google.common.base.Preconditions.checkState;

/**
 * Integration interfaces and provider factories for databases facet technologies. This decouples servicelet binding
 * code and DSL from actual JDBI (or whatever next/better tech will be) implementation modules.
 */
@Data
@Value.Enclosing
public class Databases {
  private Databases() {}

  public interface RepositoryFactory {
    Object create(Servicelet.Name owner, Key<?> key);
  }

  public static <T> Provider<T> repositoryFromServicelet(Key<? super T> key, Servicelet.Name name) {
    return new Provider<>() {
      @Inject
      RepositoryFactory factory;

      @SuppressWarnings("unchecked") // reflection proxy to target interface
      @Override
      public T get() {
        Class<?> interfaceType = key.getTypeLiteral().getRawType();
        return (T) interfaceType.cast(factory.create(name, key));
      }
    };
  }

  public static <T> Provider<T> repositoryOwnProvider(Key<? super T> key) {
    return new Provider<>() {
      @Inject
      RepositoryFactory factory;
      @Inject
      Servicelet.Name name;

      @SuppressWarnings("unchecked") // reflection proxy to target interface
      @Override
      public T get() {
        Class<?> interfaceType = key.getTypeLiteral().getRawType();
        return (T) interfaceType.cast(factory.create(name, key));
      }
    };
  }

  @Value.Immutable
  public interface Setup extends Origin.Traced<Setup> {
    String JDBC_SCHEME_PREFIX = "jdbc:";

    /**
     * Connection string (URI in JDBC format, for example "jdbc:postgresql://localhost:3434/defaultdb") The "jdbc:"
     * prefix is optional Absent port would be Note, athe port for localhost must be present when launching locally for
     * dev). Explicit zero port (":0") will stand for randomly assigned port.
     * @see #normalizedConnect()
     */
    String connect();

    /**
     * While username and password can be passed in connection string, if possible pass them separately so password
     * will not be exposed in auxiliary runtime info.
     */
    @Value.Redacted
    @Value.Default
    default String username() {
      return "";
    }

    /**
     * While username and password can be passed in connection string, if possible pass them separately so password
     * will not be exposed in auxiliary runtime info.
     */
    @Value.Redacted
    @Value.Default
    default String password() {
      return "";
    }

    /**
     * Parses connect string. The parsed URI doesn't contain "jdbc:" scheme prefix. URI construction will throw
     * exceptions and this will result in defaulting configuration in case of URL syntax error etc.
     */
    @Value.Auxiliary
    @Value.Derived
    default URI normalizedConnect() {
      // double pseudo-protocol (if specified) make it not a valid URI (like you cannot then get
      // port etc), so we remove it
      return URI.create(connect().replaceFirst("^" + JDBC_SCHEME_PREFIX, ""));
    }

    /**
     * Should autostarting on localhost be attempted (by launching docker container, for example). If URI is not for
     * "localhost", this could be ignored.
     */
    @Value.Default
    default boolean autostart() {
      return false;
    }

    @Value.Check
    default void check() {
      checkState(mode() == Mode.EXISTING || !database().isEmpty(),
          "cannot use mode %s with empty database name", mode());
    }

    /**
     * Database name to use for each servicelets. Can be empty, which is just to reuse whatever database from connection
     * string. When specified, the database will be switched to the preconfigured name or containing. See also {@link
     * #DATABASE_SERVICELET}, {@link #DATABASE_RANDOM}, {@link Mode}.
     */
    @Value.Default
    default String database() {
      return mode() == Mode.EXISTING
          ? ""
          : String.format("%s__%s", DATABASE_SERVICELET, DATABASE_RANDOM);
    }

    @Value.Default
    default Mode mode() {
      return Mode.EXISTING;
    }

    @Value.Default
    default Isolate isolate() {
      return Isolate.SCHEMA;
    }

    enum Isolate {
      SCHEMA,
      DATABASE
    }

    enum Mode {
      EXISTING,
      CREATE_AND_DROP,
      CREATE_IF_NOT_EXIST
    }

    String DATABASE_SERVICELET = "<servicelet>";
    String DATABASE_RANDOM = "<random>";

    class Builder extends ImmutableDatabases.Setup.Builder {}
  }

  public static boolean requiresDatabase(Collection<Manifest> manifests) {
    return manifests.stream()
        .flatMap(m -> m.resources().stream())
        .map(Manifest.Resource::kind)
        .anyMatch(k -> k == Manifest.Kind.DATABASE_REQUIRE || k == Manifest.Kind.DATABASE_RECORD);
  }
}
