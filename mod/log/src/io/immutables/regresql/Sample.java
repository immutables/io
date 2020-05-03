package io.immutables.regresql;

import java.sql.SQLException;

public interface Sample extends TransactionalAccessor {
	int method1() throws SQLException;
	int method2() throws SQLException;
}
