package io.immutables.regres;

import io.immutables.Nullable;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Lightweight interface abstracting datasources/any connection management, as well as preparing
 * connection, i.e. targeting it on specific schemas or other session level setup.
 * It's a breeze to implement using lambda calling
 * {@code () -> DriverManager.getConnection(uriString)}
 * with connection string or {@code DataSource::getConnection}. So easy we don't even provide
 * factories for these.
 */
public interface ConnectionProvider {
  Connection get() throws SQLException;
  /**
   * Recycles the connection: either close it or release to a pool.
   * By using recycle method we don't force implementors to create connection wrappers which
   * suppress close and override it to release a connection or do any tear down action.
   * But by default we just calling {@link Connection#close()}.
   */
  default void recycle(Connection c) throws SQLException {
    c.close();
  }

  /**
   * {@link AutoCloseable} thread-local connection handle for using with ARM-blocks.
   */
  default Handle handle() throws SQLException {
    return Handle.get(this);
  }

  final class Handle implements AutoCloseable {
    private static final ThreadLocal<Connection> openedConnection = new ThreadLocal<>();
    private final ConnectionProvider provider;
    private final boolean newone;
    public final Connection connection;

    private Handle(ConnectionProvider provider, Connection connection, boolean newone) {
      this.provider = provider;
      this.connection = connection;
      this.newone = newone;
    }

    @Override
    public void close() throws SQLException {
      if (newone) {
        openedConnection.remove();
        provider.recycle(connection);
      }
    }

    static Handle get(ConnectionProvider provider) throws SQLException {
      @Nullable Connection existing = openedConnection.get();
      if (existing != null) {
        return new Handle(provider, existing, false);
      }
      Connection newone = provider.get();
      openedConnection.set(newone);
      return new Handle(provider, newone, true);
    }
  }
}
