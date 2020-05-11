package io.immutable.regresql;

import io.immutables.regresql.SqlAccessor;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public interface Sample2 extends SqlAccessor {
	@UpdateCount int method1() throws SQLException;

//	@Single(ignoreMore = true, optional = true)
//	Optional<Map<String, String>> method1() throws SQLException;
	
	@Single(ignoreMore = true, optional = true)
	Optional<Map<String, String>> method2() throws SQLException;
}
