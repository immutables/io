package io.immutables.regresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class SqlSample {
  public static void main(String... args) throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres");
        PreparedStatement statement = connection.prepareStatement("create table xxx(a text, b int)")) {
      int updated = statement.executeUpdate();
      System.out.println("Updated records " + updated);
    }
  }
}
