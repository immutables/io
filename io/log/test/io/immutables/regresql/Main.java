package io.immutables.regresql;

import com.google.common.base.Stopwatch;
import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.codec.Resolver;
import java.sql.DriverManager;

public class Main {

	public static void main(String... args) throws Exception {
		Stopwatch sw = Stopwatch.createStarted();
		Resolver codecs = Codecs.builtin()
				.add(new OkJson.JsonStringFactory(), ImmutableJsonb.of(), 0)
				.toResolver();
		
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
