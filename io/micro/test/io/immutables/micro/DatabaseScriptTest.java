package io.immutables.micro;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import static io.immutables.that.Assert.that;
import static org.mockito.Mockito.when;

public class DatabaseScriptTest {
  final Connection connection = Mockito.mock(Connection.class);
  final Statement statement = Mockito.mock(Statement.class);

  @Before
  public void stub() throws SQLException {
    when(connection.createStatement()).thenReturn(statement);
  }

  @Test
  public void fromSql() throws Exception {
    DatabaseScript script = DatabaseScript.usingSql("-- inline sql");
    that(runAndCapture(script)).is("-- inline sql");
  }

  @Test
  public void fromSqlResource() throws Exception {
    DatabaseScript script = DatabaseScript.fromResource(getClass(), "DatabaseScriptTest.Sample.sql");
    that(runAndCapture(script)).is("-- some sql script");
  }

  private String runAndCapture(DatabaseScript script) throws SQLException {
    script.execute(connection);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    Mockito.verify(statement).execute(sql.capture());
    return sql.getValue();
  }
}
