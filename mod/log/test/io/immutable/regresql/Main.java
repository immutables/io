package io.immutable.regresql;

import com.google.common.base.Stopwatch;
import io.immutables.codec.Codec.Compound;
import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.regresql.Regresql;

public class Main {

	public static void main(String... args) throws Exception {
		Stopwatch sw = Stopwatch.createStarted();
		Compound codecs = Codecs.builtin();
		Jsonb jsonb = Jsonb.Impl.jsonb();
		codecs.add(new OkJson.JsonStringFactory(), jsonb, 0);
		try {
			Sample2 sample = Regresql.create(Sample2.class, codecs);
			System.out.println(sw);
			System.out.println("RESULTS \n");
			System.out.println(sample.method1());
			System.out.println(sample.method2());
		} finally {
			System.out.println(sw);
		}
	}
}
