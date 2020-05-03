package io.immutables.regresql;

import java.sql.Connection;
import java.sql.SQLException;

public interface TransactionHandle extends AutoCloseable {

	Connection connection() throws SQLException;

	void rollback() throws SQLException;

	@Override
	void close() throws SQLException;
}
