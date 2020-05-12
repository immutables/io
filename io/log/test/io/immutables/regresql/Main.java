package io.immutables.regresql;

import com.google.common.base.Stopwatch;
import io.immutables.codec.Codec.Compound;
import io.immutables.regresql.ImmutableJsonb;
import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.regresql.Regresql;
import java.sql.DriverManager;

public class Main {

	public static void main(String... args) throws Exception {
		Stopwatch sw = Stopwatch.createStarted();
		Compound codecs = Codecs.builtin();
		Jsonb jsonb = ImmutableJsonb.of();
		codecs.add(new OkJson.JsonStringFactory(), jsonb, 0);
		try {
			Sample2 sample = Regresql.create(Sample2.class, codecs,
					() -> DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres"));
			System.out.println(sw);
			System.out.println("RESULTS \n");
			System.out.println(sample.method1());
			System.out.println(sample.method2());
		} finally {
			System.out.println(sw);
		}
	}
}
