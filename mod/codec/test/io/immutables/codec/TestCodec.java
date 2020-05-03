package io.immutables.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.squareup.moshi.JsonWriter;
import io.immutables.codec.Codec.Lookup;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okio.Buffer;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestCodec {
	final Lookup l = Codecs.lookup();
	final Buffer buffer = new Buffer();

	@Test
	public void stringList() throws IOException {
		Codec<List<String>> codec = l.get(new TypeToken<List<String>>() {});

		ImmutableList<String> list = ImmutableList.of("a", "b", "c");

		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), list);

		String json = buffer.readUtf8();
		that(json).is("['a','b','c']".replace('\'', '"'));
		that(codec.decode(OkJson.in(json))).isOf(list);
	}

	@Test
	public void intList() throws IOException {
		Codec<List<Integer>> codec = l.get(new TypeToken<List<Integer>>() {});

		ImmutableList<Integer> list = ImmutableList.of(1, 2, 3);
		codec.encode(OkJson.out(JsonWriter.of(buffer)), list);

		String json = buffer.readUtf8();
		that(json).is("[1,2,3]");
		that(codec.decode(OkJson.in(json))).isOf(list);
	}

	@Test
	public void stringIntMap() throws IOException {
		Codec<Map<String, Integer>> codec = l.get(new TypeToken<Map<String, Integer>>() {});

		ImmutableMap<String, Integer> map = ImmutableMap.<String, Integer>of("a", 1, "b", 2);

		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), map);

		String json = buffer.readUtf8();
		that(json).is("{'a':1,'b':2}".replace('\'', '"'));
		that(codec.decode(OkJson.in(json))).equalTo(map);
	}

	@Test
	public void intBooleanMap() throws IOException {
		Codec<Map<Integer, Boolean>> codec = l.get(new TypeToken<Map<Integer, Boolean>>() {});

		ImmutableMap<Integer, Boolean> map = ImmutableMap.<Integer, Boolean>of(1, true, 3, false);
		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), map);

		String json = buffer.readUtf8();
		that(json).is("{'1':true,'3':false}".replace('\'', '"'));
		that(codec.decode(OkJson.in(json))).equalTo(map);
	}
}
