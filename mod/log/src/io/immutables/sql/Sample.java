package io.immutables.sql;

import java.sql.SQLException;

public interface Sample extends SqlAccessor {
	int method1() throws SQLException;
	int method2() throws SQLException;
}
