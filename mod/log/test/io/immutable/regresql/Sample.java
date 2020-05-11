package io.immutable.regresql;

import io.immutables.regresql.SqlAccessor;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Sample extends SqlAccessor {
	@UpdateCount
	long[] createTable() throws SQLException;

	@UpdateCount
	int[] insertValues() throws SQLException;

	@UpdateCount
	int insertValuesAndSelect() throws SQLException;

	List<Map<String, String>> insertValuesAndSelectSkippingUpdateCount() throws SQLException;

	List<Map<String, String>> multipleSelectFail() throws SQLException, IOException;

	void multipleSelectVoid() throws SQLException;

	@Single
	Map<String, String> selectSingle() throws SQLException;

	@Single(optional = true)
	Optional<Map<String, String>> selectEmpty() throws SQLException;

	@Single
	@Column(index = 1)
	String selectSingleColumn() throws SQLException;

	@Single(ignoreMore = true)
	@Column
	int selectFirstColumnIgnoreMore() throws SQLException;

	@Column("b")
	List<String> selectColumns() throws SQLException;

	@Column("c")
	@Single(ignoreMore = true)
	@Jsonb
	List<Integer> selectJsonbColumn() throws SQLException;

	@UpdateCount
	long dropTable() throws SQLException;

	@Single
	@Column
	String selectConcatSimple(@Named("a") String a1, @Named("b") String b2, @Named("c") String c3);

	@Single
	@Column
	String selectConcatSpread(@Spread Map<String, String> map);

	@Single
	@Column
	String selectConcatSpreadPrefix(
			@Spread Map<String, String> map1,
			@Spread(prefix = "u.") Map<String, String> map2,
			@Named("d") String d);

	void createTableForBatch() throws SQLException;

	@UpdateCount
	int[] insertBatch(@Named("a") @Batch List<String> as, @Named("b") int b) throws SQLException;

	@UpdateCount
	int[] insertBatchSpread(@Spread Map<String, String> map1, @Named("b") @Batch int... values) throws SQLException;

	void dropTableForBatch() throws SQLException;

	@Column
	List<String> selectFromBatch() throws SQLException;
}
