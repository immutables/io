package io.immutables.codec;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import io.immutables.codec.Codec.Lookup;
import java.io.IOException;
import java.util.List;
import okio.Buffer;

public class Mainss {

	public static void main(String... args) throws IOException {
		Stopwatch stopwatch = Stopwatch.createStarted();

		Lookup l = Codecs.lookup();

		Codec<List<String>> c = l.get(new TypeToken<List<String>>() {});

		Buffer buffer = new Buffer();

		JsonWriter writer = JsonWriter.of(buffer);
		writer.setIndent("  ");
		c.encode(OkJson.out(writer), ImmutableList.of("a", "b", "c"));

		System.out.println(buffer.readUtf8());

		Codec<List<Integer>> c2 = l.get(new TypeToken<List<Integer>>() {});

		JsonWriter writer2 = JsonWriter.of(buffer);
		writer2.setIndent("  ");
		c2.encode(OkJson.out(writer2), ImmutableList.of(1, 2, 3));

		List<Integer> l123 = c2.decode(OkJson.in(JsonReader.of(buffer)));
		System.out.println(l123);

		System.out.println(stopwatch.stop());
	}
}
