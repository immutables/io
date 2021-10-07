package io.immutables.micro;

import io.immutables.regres.Errors;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import com.google.common.io.Resources;
import static java.util.Objects.requireNonNull;

/**
 * Database script (usually initialization DDLs and migrations). Standard implementations for executing SQL
 * statements from string and from resource are provided via factory methods.
 */
public interface DatabaseScript {

  /**
   * Given connection and assuming that the target database is set as a current database it will executre database
   * script.
   * <p>
   * Note: All database initializers are supposed to be able to run independently. If some initialization must be
   * performed in a certain order, please, consider using composite initialization which calls delegate steps in order.
   * The area of launching a set migrations is not yet explored for our system and further research will improve this.
   * <p>
   * Note: initially there was an idea to use {@link DataSource} but it appeared to be harder to work with in practice
   * (for example, providing data source targeting specific database) but we can always wrap single-connection
   * datasource (suppressing close method on original connection) if it's really needed.
   */
  void execute(Connection connection) throws SQLException;

  /**
   * Execute database using SQL statements. All the rest SQL problems are raised during actual initialization.
   * @param sql SQL statements
   * @return initializer object that uses SQL statements
   */
  static DatabaseScript usingSql(String sql) {
    requireNonNull(sql, "SQL script must not be null");
    return new DatabaseScript() {
      @Override
      public void execute(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
          s.execute(sql);
        } catch (SQLException ex) {
          throw Errors.refineException(sql, "<todo>", ex);
        }
      }

      @Override
      public String toString() {
        return DatabaseScript.class.getSimpleName() + ".usingSql(" + sql + ")";
      }
    };
  }

  /**
   * Initializes database using SQL resource/script. Reads SQL resource early on, so unchecked IO exception for reading
   * resource is thrown during construction, not during DB initialization. All the rest SQL problems are raised during
   * actual initialization.
   * @param origin resource origin
   * @return initializer object that uses SQL resource
   * @name filename relative or absolute filename
   */
  static DatabaseScript fromResource(Class<?> origin, String filename) {
    requireNonNull(origin, "origin");
    requireNonNull(filename, "filename");
    String sql;
    try {
      sql = Resources.asCharSource(origin.getResource(filename), StandardCharsets.UTF_8).read();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read SQL resource/script %s" + filename, e);
    }

    return new DatabaseScript() {
      @Override public void execute(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
          s.execute(sql);
        } catch (SQLException ex) {
          throw Errors.refineException(sql, filename, ex);
        }
      }

      @Override public String toString() {
        return DatabaseScript.class.getSimpleName() + ".fromResource(" + origin + " " + filename + ")";
      }
    };
  }
}
