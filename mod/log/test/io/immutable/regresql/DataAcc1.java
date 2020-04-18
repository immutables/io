package io.immutable.regresql;

public interface DataAcc1 {

  void createTable();

  void insert(String value);
  
  void dropTable();
}
