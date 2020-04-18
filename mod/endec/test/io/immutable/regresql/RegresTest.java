package io.immutable.regresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class RegresTest {
  public static void main(String... args) throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres")) {
      try (Statement statement = connection.createStatement()) {
        int update = statement.executeUpdate("insert into xxx(a,b) values ('HHHHHH', 243435)");
        System.out.println("Updated!!! " + update);
      }
    }
  }
}
