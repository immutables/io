package io.immutables.regresql;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.immutables.regresql.ImmutableJsonb;
import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.codec.Resolver;
import io.immutables.regresql.Regresql;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.junit.Test;
import static io.immutables.that.Assert.that;

//@Ignore
public class TestRegresql {
	static final Jsonb jsonb = ImmutableJsonb.of();
	static final Resolver codecs;
	static {
		codecs = Codecs.builtin().add(new OkJson.JsonStringFactory(), jsonb, 0).toResolver();
	}

	final Sample sample = Regresql.create(Sample.class, codecs,
			() -> DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres"));

	@Test
	public void results() throws Exception {
		that(sample.createTable()).isOf(0L, 0L);
		that(sample.insertValues()).isOf(1, 1, 1);
		that(sample.insertValuesAndSelect()).is(2);
		that(sample.insertValuesAndSelectSkippingUpdateCount()).isEmpty();
		that(() -> sample.multipleSelectFail()).thrown(Exception.class);
		sample.multipleSelectVoid(); // no exceptions
		Map<String, String> m1 = sample.selectSingle();
		that(m1.keySet()).isOf("a", "b", "c");
		that(m1.values()).isOf("1", "A", "[1]");
		that(sample.selectEmpty()).isEmpty();
		that(sample.selectSingleColumn()).is("C");
		that(sample.selectFirstColumnIgnoreMore()).is(1);
		that(sample.selectColumns()).isOf("A", "B", "C", "D", "E", "F");
		that(sample.selectJsonbColumn()).isOf(1);
		that(sample.dropTable()).is(0);
	}

	@Test
	public void parameters() {
		that(sample.selectConcatSimple("a", "b", "c")).is("abc");
		that(sample.selectConcatSpread(ImmutableMap.of("a", "1", "b", "2", "c", "3"))).is("123");
		that(sample.selectConcatSpreadPrefix(ImmutableMap.of("a", "1"), ImmutableMap.of("b", "2", "c", "3"), "4"))
				.is("1234");
	}

	@Test
	public void batch() throws SQLException {
		sample.createTableForBatch();
		that(sample.insertBatch(ImmutableList.of("X", "Y", "Z"), 0)).isOf(1, 1, 1);
		that(sample.insertBatchSpread(ImmutableMap.of("a", "U"), 1, 2, 3)).isOf(1, 1, 1);
		that(sample.selectFromBatch()).hasOnly("X-0", "Y-0", "Z-0", "U-1", "U-2", "U-3");
		sample.dropTableForBatch();
	}
}
