package io.immutables.regres;

import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.codec.Resolver;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestRegresql {
	static final Jsonb jsonb = ImmutableJsonb.of();
	static final Resolver codecs;
	static {
		codecs = Codecs.builtin().add(new OkJson.JsonStringFactory(), jsonb, 0).toResolver();
	}
	// Connection pool would do the same better I guess :)
  static final AtomicReference<Connection> connection = new AtomicReference<>();
  static final Sample sample = Regresql.create(Sample.class, codecs, new ConnectionProvider() {
    @Override public Connection get() {
      return connection.getAcquire();
    }
    @Override public void recycle(Connection c) {
      connection.setRelease(c);
    }
  });

  @BeforeClass
  public static void openConnection() throws SQLException {
    connection.set(DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres"));
  }

  @AfterClass
  public static void closeConnection() throws SQLException {
    connection.get().close();
  }

	@Test
	public void results() throws Exception {
		that(sample.createTable()).isOf(0L, 0L);
		that(sample.insertValues()).isOf(1, 1, 1);
		that(sample.insertValuesAndSelect()).is(2);
		that(sample.insertValuesAndSelectSkippingUpdateCount()).isEmpty();
		that(sample::multipleSelectFail).thrown(Exception.class);
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
		that(sample.selectConcatSpread(Map.of("a", "1", "b", "2", "c", "3"))).is("123");
		that(sample.selectConcatSpreadPrefix(Map.of("a", "1"), Map.of("b", "2", "c", "3"), "4"))
				.is("1234");
	}

	@Test
	public void batch() throws SQLException {
		sample.createTableForBatch();
		that(sample.insertBatch(List.of("X", "Y", "Z"), 0)).isOf(1, 1, 1);
		that(sample.insertBatchSpread(Map.of("a", "U"), 1, 2, 3)).isOf(1, 1, 1);
		that(sample.selectFromBatch()).hasOnly("X-0", "Y-0", "Z-0", "U-1", "U-2", "U-3");
		sample.dropTableForBatch();
	}

  @Test
  public void jsonb() throws SQLException {
    sample.createTable();
    String jsonb = sample.insertAndGetJsonb(Map.of("a", 1));
    that(jsonb).is("{\"a\": 1}"); // mind formatting
    sample.dropTable();
  }
}
