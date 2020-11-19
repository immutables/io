package io.immutables.micro.wiring;

import io.immutables.codec.Resolver;
import io.immutables.micro.*;
import io.immutables.regres.ConnectionProvider;
import io.immutables.regres.Regresql;
import io.immutables.regres.SqlAccessor;

import io.immutables.micro.wiring.docker.DockerRunner;
import java.lang.reflect.Method;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.ws.rs.core.UriBuilder;
import com.google.common.base.CharMatcher;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.immutables.data.Data;
import org.immutables.value.Value;

@Data
@Value.Enclosing
public final class DatabaseModule implements Module {
  @Override
  public void configure(Binder binder) {}

  @Provides
  @Singleton
  public ConnectionInfo connectionInfo(Databases.Setup setup) {
    Optional<Integer> autostartPort = autostartPort(setup);
    URI connect = setup.normalizedConnect();
    Optional<String> dbListenAddress = autostartPort.map(DockerRunner::assertPostgresRunning);
    return new ConnectionInfo.Builder()
        .setup(setup)
        .connect(autostartPort.isPresent()
            ? UriBuilder.fromUri(connect)
            .host(dbListenAddress.orElse(connect.getHost()))
            .port(autostartPort.get())
            .build()
            : connect)
        .build();
  }

  @Provides
  @Singleton
  public ConnectionProvider connections(ConnectionInfo database) {
    return () -> DriverManager.getConnection(database.toJdbcString());
  }

  @ProvidesIntoSet
  public MixinModule perServicelet() {
    return b -> Multibinder.newSetBinder(b, DatabaseScript.class);
  }

  @Provides
  @Singleton
  public @Systems Map<Servicelet.Name, DatabaseForServicelet> registry() {
    return new ConcurrentHashMap<>();
  }

  @Provides
  @Singleton
  public DatabaseManager manager(
      ConnectionInfo connectionInfo,
      ConnectionProvider connections,
      @Systems Map<Servicelet.Name, DatabaseForServicelet> registry,
      @Systems Provider<Set<Injector>> servicelets) {
    return new DatabaseManager(connectionInfo, connections, servicelets, registry);
  }

  @ProvidesIntoSet
  public Service asService(DatabaseManager manager) {
    return manager;
  }

  @Provides
  @Singleton
  public Databases.RepositoryFactory repositoryFactory(Resolver codecs, DatabaseManager manager) {
    return (servicelet, key) -> {
      return new DatabaseSwitchHandler(
          key.getTypeLiteral().getRawType(),
          manager.connectionInfo.setup(),
          manager.connections,
          codecs,
          manager.databaseFor(servicelet)).newProxy();
    };
  }

  @Provides
  public @Systems.Shared Databases.RepositoryFactory sharedRepositoryFactory(Databases.RepositoryFactory factory) {
    return factory;
  }

  private static class DatabaseManager extends AbstractIdleService {
    private final Provider<Set<Injector>> servicelets;
    private final Map<Servicelet.Name, DatabaseForServicelet> registry;
    private final List<AutoCloseable> onShutdown = new ArrayList<>();
    final ConnectionProvider connections;
    final ConnectionInfo connectionInfo;

    DatabaseManager(
        ConnectionInfo connectionInfo,
        ConnectionProvider connections,
        Provider<Set<Injector>> servicelets,
        Map<Servicelet.Name, DatabaseForServicelet> perServicelet) {
      this.connectionInfo = connectionInfo;
      this.connections = connections;
      this.servicelets = servicelets;
      this.registry = perServicelet;
    }

    Supplier<String> databaseFor(Servicelet.Name name) {
      return Suppliers.memoize(() -> {
        // make sure all database initializations are complete before the first access
        awaitRunning();
        // this is the only gimmick why we use memoizing supplier,
        // now we know for sure that db(s) and their names are initialized
        return registry.get(name).database();
      });
    }

    @Override
    protected void startUp() throws Exception {
      servicelets.get()
          .stream()
          .filter(this::requiresDatabase)
          .map(this::defineDatabase)
          .forEach(onShutdown::add);
    }

    @Override
    protected void shutDown() throws Exception {
      @Nullable Exception exception = null;
      for (AutoCloseable closeable : onShutdown) {
        try {
          closeable.close();
        } catch (Exception e) {
          if (exception == null) exception = e;
          else exception.addSuppressed(e);
        }
      }
      if (exception != null) throw exception;
    }

    AutoCloseable defineDatabase(Injector servicelet) {
      Servicelet.Name name = ServiceletNameModule.getName(servicelet);
      DatabaseForServicelet definition = defineFor(name);

      try (var handle = connections.handle()) {
        if (createIfNecessary(handle, definition)) {
          switchDatabase(connectionInfo.setup(), handle, definition.database());

          for (DatabaseScript init : servicelet.getInstance(KEY_DATABASE_INIT)) {
            init.execute(handle.connection);
          }
        }
      } catch (SQLException ex) {
        throw new Error(
            String.format("Cannot initialize database %s for servicelet %s", definition.database(), name),
            ex);
      }

      return () -> dropIfNecessary(definition);
    }

    private void dropIfNecessary(DatabaseForServicelet definition) throws SQLException {
      if (definition.setup().mode() == Databases.Setup.Mode.CREATE_AND_DROP) {
        try (var handle = connections.handle();
            Statement s = handle.connection.createStatement()) {
          switch (connectionInfo.setup().isolate()) {
          case SCHEMA:
            s.execute("drop schema " + definition.database() + " cascade");
            break;
          case DATABASE:
            s.execute("drop database  " + definition.database());
            break;
          }
        }
      }
      // Opportunistically we will delete all such databases on shutdown,
      // but it's ok if we will not close some on crash because:
      // 1. Should be easy to kill roach docker container at some point
      // 2. Might be useful to have the db(s) for inspection in case of crash
    }

    private boolean createIfNecessary(ConnectionProvider.Handle handle, DatabaseForServicelet definition) throws SQLException {
      switch (definition.setup().mode()) {
      case CREATE_AND_DROP:
        try (Statement s = handle.connection.createStatement()) {
          switch (connectionInfo.setup().isolate()) {
          case SCHEMA:
            s.execute("create schema " + definition.database());
            return true;

          case DATABASE:
            s.execute("create database " + definition.database());
            return true;
          }
        }
        // doesn't work via '?' placeholder
        // we should enjoy blank, newly created database and drop if afterwards,
        // so fail if we cannot create a new one,
        // use CREATE_IF_NOT_EXIST if overlaps possible (but no auto-drop then)

      case CREATE_IF_NOT_EXIST:
        try (Statement s = handle.connection.createStatement()) {
          switch (connectionInfo.setup().isolate()) {
          case SCHEMA:
            s.execute("create schema if not exists " + definition.database());
            return true;
          case DATABASE:
            return s.executeUpdate("create database if not exists " + definition.database()) > 0;
          }
        }

      case EXISTING:
      default:
        return false;
      }
    }

    private DatabaseForServicelet defineFor(Servicelet.Name name) {
      String databaseName = connectionInfo.setup()
          .database()
          .replace(Databases.Setup.DATABASE_SERVICELET, toDatabaseName(name))
          .replace(Databases.Setup.DATABASE_RANDOM, randomToken());

      DatabaseForServicelet definition = new DatabaseForServicelet.Builder()
          .servicelet(name)
          .database(databaseName)
          .setup(connectionInfo.setup())
          .build();

      assert !registry.containsKey(name);
      registry.put(name, definition);
      return definition;
    }

    private String randomToken() {
      // starts with underscore to make sure if placeholder is in front of a pattern,
      // we will not start database name with a number
      return "_" + Math.abs((int) (Math.random() * Integer.MAX_VALUE));
    }

    private String toDatabaseName(Servicelet.Name name) {
      return CharMatcher.anyOf(".-/@")
          .replaceFrom(name.toString().toLowerCase(), '_') + ""; // instanceSuffix;
    }

    private boolean requiresDatabase(Injector servicelet) {
      Manifest manifest = servicelet.getInstance(Manifest.class);
      return Databases.requiresDatabase(Set.of(manifest))
          || !servicelet.getInstance(KEY_DATABASE_INIT).isEmpty();
    }
  }

  /**
   * Database selector proxy implementation for our SQL repositories. The delegate is itself a proxy, which technically
   * would mean that this is a proxy to a proxy. The only justification is the need to apply connection context
   * (database) and attaching it per repository instance depending on the servicelet which uses it.
   * <p>
   * As a matter of implementation we do only the simplest handling and try to delegate almost everything to (including
   * hustle with default methods on interfaces).
   */
  private static class DatabaseSwitchHandler extends DelegatingProxyHandler {
    private final Object delegate;
    private final Supplier<String> databaseSupplier;
    private final Databases.Setup setup;
    private final ConnectionProvider connections;

    DatabaseSwitchHandler(
        Class<?> interfaceType,
        Databases.Setup setup,
        ConnectionProvider connections,
        Resolver codecs,
        Supplier<String> databaseSupplier) {
      super(interfaceType);
      this.setup = setup;
      this.connections = connections;
      this.delegate = Regresql.create(interfaceType, codecs, connections);
      this.databaseSupplier = databaseSupplier;
    }

    @Override
    String label() {
      return "<DB: " + databaseSupplier.get() + ">";
    }

    @Override
    Object delegate() {
      return delegate;
    }

    @Override
    Object invokeDelegate(Method method, Object[] args) throws Exception {
      if (isConnectionHandleMethod(method)) {
        ConnectionProvider.Handle handle = connections.handle();
        String database = databaseSupplier.get();
        if (!database.isEmpty()) {
          switchDatabase(setup, handle, database);
        }
        return handle;
      }

      // can just delegate the rest to the delegate, but first we're setting the session database
      try (ConnectionProvider.Handle handle = connections.handle()) {
        String database = databaseSupplier.get();
        if (!database.isEmpty()) {
          switchDatabase(setup, handle, database);
        }
        // if curious, connection will be put opened to thread-local and will be used for nested calls
        return super.invokeDelegate(method, args);
      }
    }
  }

  private static boolean isConnectionHandleMethod(Method method) {
    return SqlAccessor.class.isAssignableFrom(method.getDeclaringClass())
        && method.getName().equals("connectionHandle")
        && method.getParameterCount() == 0;
  }

  private static void switchDatabase(Databases.Setup setup, ConnectionProvider.Handle handle, String database) throws SQLException {
    switch (setup.isolate()) {
    case SCHEMA:
      try (Statement statement = handle.connection.createStatement()) {
        statement.execute("set search_path to " + database);
      }
      break;
    case DATABASE:
      try (PreparedStatement statement = handle.connection.prepareStatement("set database = ?")) {
        statement.setString(1, database);
        statement.execute();
      }
      break;
    }
  }

  /**
   * Specification for database connection used for all servicelets.
   */
  @Value.Immutable
  public interface ConnectionInfo {
    /** Reference to the configuration. */
    Databases.Setup setup();

    /** Actual connect URI. */
    URI connect();

    default String toJdbcString() {
      return Databases.Setup.JDBC_SCHEME_PREFIX + connect().toString();
    }

    class Builder extends ImmutableDatabaseModule.ConnectionInfo.Builder {}
  }

  /**
   * Specification for database to connect used for servicelet
   */
  @Value.Immutable
  public interface DatabaseForServicelet extends Origin.Traced<DatabaseForServicelet> {
    /** Reference to the servicelet (by name) for which this was created. */
    Servicelet.Name servicelet();

    /** Reference to the configuration. */
    Databases.Setup setup();

    /**
     * Database name used to set connection access. Will be empty string if we just use whatever is in connection string
     * or whatever the driver/database default is. This will be fully computed name if setup contains.
     */
    String database();

    @Value.Auxiliary
    @Value.Default
    @Override
    default Origin origin() {
      return setup().origin();
    }

    class Builder extends ImmutableDatabaseModule.DatabaseForServicelet.Builder {}
  }

  private Optional<Integer> autostartPort(Databases.Setup setup) {
    URI uri = setup.normalizedConnect();
    if (LocalPorts.isLocalhost(uri.getHost()) && setup.autostart()) {
      int port = uri.getPort();
      if (port < 0) return Optional.of(POSTGRES_STANDARD_PORT);
      if (port == 0) return Optional.of(LocalPorts.findSomeFreePort());
      return Optional.of(port);
    }
    return Optional.empty();
  }

  private static final Key<Set<DatabaseScript>> KEY_DATABASE_INIT = Keys.setOf(DatabaseScript.class);
  private static final int POSTGRES_STANDARD_PORT = 5432;
}
