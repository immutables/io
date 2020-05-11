package io.immutables.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import io.immutables.codec.Codec.Resolver;
import io.immutables.codec.Dutu.Bubu;
import io.immutables.codec.Dutu.Opts;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import okio.Buffer;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestCodec {
	static final Resolver l = Codecs.builtin();
	final Buffer buffer = new Buffer();

	@Test
	public void stringList() throws IOException {
		Codec<List<String>> codec = l.get(new TypeToken<List<String>>() {});

		ImmutableList<String> list = ImmutableList.of("a", "b", "c");

		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), list);

		String json = buffer.readUtf8();
		that(json).is("['a','b','c']".replace('\'', '"'));
		that(codec.decode(in(json))).isOf(list);
	}

	@Test
	public void intList() throws IOException {
		Codec<List<Integer>> codec = l.get(new TypeToken<List<Integer>>() {});

		ImmutableList<Integer> list = ImmutableList.of(1, 2, 3);
		codec.encode(OkJson.out(JsonWriter.of(buffer)), list);

		String json = buffer.readUtf8();
		that(json).is("[1,2,3]");
		that(codec.decode(in(json))).isOf(list);
	}

	@Test
	public void stringIntMap() throws IOException {
		Codec<Map<String, Integer>> codec = l.get(new TypeToken<Map<String, Integer>>() {});

		ImmutableMap<String, Integer> map = ImmutableMap.<String, Integer>of("a", 1, "b", 2);

		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), map);

		String json = buffer.readUtf8();
		that(json).is("{'a':1,'b':2}".replace('\'', '"'));
		that(codec.decode(in(json))).equalTo(map);
	}

	@Test
	public void intBooleanMap() throws IOException {
		Codec<Map<Integer, Boolean>> codec = l.get(new TypeToken<Map<Integer, Boolean>>() {});

		ImmutableMap<Integer, Boolean> map = ImmutableMap.<Integer, Boolean>of(1, true, 3, false);
		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), map);

		String json = buffer.readUtf8();
		that(json).is("{'1':true,'3':false}".replace('\'', '"'));
		that(codec.decode(in(json))).equalTo(map);
	}

	public static class Struct<T> {
		public int a;
		public boolean b;
		public List<T> c;
	}

	@Test
	public void structs() throws IOException {
		Codec<Struct<String>> codec = l.get(new TypeToken<Struct<String>>() {});

		Struct<String> s = new Struct<>();
		s.a = 1;
		s.b = true;
		s.c = ImmutableList.of("d", "e");

		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), s);

		String json = buffer.readUtf8();
		that(json).is("{'a':1,'b':true,'c':['d','e']}".replace('\'', '"'));

		Struct<String> s2 = codec.decode(in(json));

		that(s2.a).is(s.a);
		that(s2.b).is(s.b);
		that(s2.c).isOf(s.c);
	}

	@Test
	public void datatypes() throws IOException {
		Codec<Dutu> codec = l.get(new TypeToken<Dutu>() {});

		Dutu dutu = new Dutu.Builder()
				.i(42)
				.s("X")
				.build();

		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), dutu);

		String json = buffer.readUtf8();
		that(json).is("{'i':42,'s':'X'}".replace('\'', '"'));

		Dutu dutu2 = codec.decode(in(json));

		that(dutu2.d()).just().isNull();
		that(dutu2.i()).is(42);
		that(dutu2.s()).is("X");
	}

	@Test
	public void datatypes2() throws IOException {
		Codec<Bubu<String>> codec = l.get(new TypeToken<Bubu<String>>() {});

		Bubu<String> bubu = Bubu.of("BOO");

		JsonWriter writer = JsonWriter.of(buffer);
		codec.encode(OkJson.out(writer), bubu);

		String json = buffer.readUtf8();
		that(json).is("{'b':'BOO'}".replace('\'', '"'));

		Bubu<String> bubu2 = codec.decode(in(json));

		that(bubu2.b()).is("BOO");
	}

	@Test
	public void optionals() throws IOException {
		Codec<Opts> codec = l.get(new TypeToken<Opts>() {});

		Opts opts = new Opts.Builder()
				.i(42)
				.s("X")
				.build();

		JsonWriter writer = JsonWriter.of(buffer);
		writer.setSerializeNulls(true);
		codec.encode(OkJson.out(writer), opts);

		String json = buffer.readUtf8();
		that(json).is("{'s':'X','i':42,'l':null,'d':null}".replace('\'', '"'));
	}
	
	@Test
	public void optionals2() throws IOException {
		Codec<Opts> codec = l.get(new TypeToken<Opts>() {});

		Opts opts2 = codec.decode(in("{s:'X',i:42,l:12,d:0.5}"));

		that(opts2.s()).isOf("X");
		that(opts2.i()).equalTo(OptionalInt.of(42));
		that(opts2.l()).equalTo(OptionalLong.of(12));
		that(opts2.d()).equalTo(OptionalDouble.of(0.5));

		Opts opts3 = codec.decode(in("{s:null,i:null,l:null,d:null}"));

		that(opts3.s()).isEmpty();
		that(opts3.i()).same(OptionalInt.empty());
		that(opts3.l()).same(OptionalLong.empty());
		that(opts3.d()).same(OptionalDouble.empty());

		Opts opts4 = codec.decode(in("{}"));

		that(opts4.s()).isEmpty();
		that(opts4.i()).same(OptionalInt.empty());
		that(opts4.l()).same(OptionalLong.empty());
		that(opts4.d()).same(OptionalDouble.empty());
	}

	public static Codec.In in(CharSequence chars) {
		Buffer buffer = new Buffer();
		buffer.writeUtf8(chars.toString());
		JsonReader reader = JsonReader.of(buffer);
		reader.setLenient(true);
		return OkJson.in(reader);
	}

}
