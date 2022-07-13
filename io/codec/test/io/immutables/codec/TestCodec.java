package io.immutables.codec;

import io.immutables.codec.Dutu.Bubu;
import io.immutables.codec.Dutu.Opts;
import okio.Buffer;
import java.io.IOException;
import java.util.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestCodec {
	static final Resolver l = Codecs.builtin().toResolver();
	final Buffer buffer = new Buffer();

	@Test
	public void stringList() throws IOException {
		Codec<List<String>> codec = l.get(new TypeToken<List<String>>() {});

		List<String> list = List.of("a", "b", "c");
		String json = toJson(codec, list);

		that(json).is("['a','b','c']");
		that(codec.decode(in(json))).isOf(list);
	}

	@Test
	public void intList() throws IOException {
		Codec<List<Integer>> codec = l.get(new TypeToken<List<Integer>>() {});

		List<Integer> list = List.of(1, 2, 3);
		String json = toJson(codec, list);

		that(json).is("[1,2,3]");
		that(codec.decode(in(json))).isOf(list);
	}

	@Test
	public void stringIntMap() throws IOException {
		Codec<Map<String, Integer>> codec = l.get(new TypeToken<Map<String, Integer>>() {});

		Map<String, Integer> map = ImmutableMap.<String, Integer>of("a", 1, "b", 2);
		String json = toJson(codec, map);

		that(json).is("{'a':1,'b':2}");
		that(codec.decode(in(json))).equalTo(map);
	}

	@Test
	public void intBooleanMap() throws IOException {
		Codec<Map<Integer, Boolean>> codec = l.get(new TypeToken<Map<Integer, Boolean>>() {});

		Map<Integer, Boolean> map = ImmutableMap.<Integer, Boolean>of(1, true, 3, false);
		String json = toJson(codec, map);

		that(json).is("{'1':true,'3':false}");
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

		String json = toJson(codec, s);
		that(json).is("{'a':1,'b':true,'c':['d','e']}");

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

		String json = toJson(codec, dutu);
		that(json).is("{'i':42,'s':'X'}");

		Dutu dutu2 = codec.decode(in(json));

		that(dutu2.d()).just().isNull();
		that(dutu2.i()).is(42);
		that(dutu2.s()).is("X");
	}

	@Test
	public void datatypes2() throws IOException {
		Codec<Bubu<String>> codec = l.get(new TypeToken<Bubu<String>>() {});

		Bubu<String> bubu = Bubu.of("BOO");
		String json = toJson(codec, bubu);
		that(json).is("{'b':'BOO'}");

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

		String json = buffer.readUtf8().replace('"', '\'');
		that(json).is("{'s':'X','i':42,'l':null,'d':null}");

		that(codec.decode(in(json))).equalTo(opts);
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

	@Test
	public void caseTypes() throws IOException {
		Codec<Cases> codec = l.get(new TypeToken<Cases>() {});

		String json = toJson(codec, new Cases.A.Builder().a(33).build());
		that(json).is("{'@case':'A','a':33}");

		json = toJson(codec, new Cases.B.Builder().b(true).build());
		that(json).is("{'@case':'B','b':true}");

		json = toJson(codec, Cases.C.of("Z"));
		that(json).is("{'@case':'C','c':'Z'}");

		json = toJson(codec, Cases.D.of());
		that(json).is("{'@case':'D'}");

		that(codec.decode(in("{'c':'CCC','@case':'C'}"))).equalTo(Cases.C.of("CCC"));
		that(codec.decode(in("{'@case':'B','b':false}"))).equalTo(new Cases.B.Builder().b(false).build());
		that(codec.decode(in("{'@case':'D'}"))).same(Cases.D.of());
	}

	@Test
	public void fieldFormat() throws IOException {
		Codec<Dutu.Format> codec = l.get(new TypeToken<Dutu.Format>() {});

		var val = new Dutu.Format.Builder()
				.reallyAnotherField(1.0f)
				.fieldName(1)
				.build();
		var json = toJson(codec, val);

		that(json).is("{'field-name':1,'really-another-field':1.0}");
		that(codec.decode(in(json))).equalTo(val);
	}

	@Test
	public void enumFormat() throws IOException {
		Codec<Dutu.Ggz> codec = l.get(new TypeToken<Dutu.Ggz>() {});

		var json = toJson(codec, Dutu.Ggz.GG_WP);
		that(json).is("'gg-wp'");
		that(codec.decode(in(json))).same(Dutu.Ggz.GG_WP);

		json = toJson(codec, Dutu.Ggz.gG);
		that(json).is("'g-g'");
		that(codec.decode(in(json))).same(Dutu.Ggz.gG);
	}

	@Test
	public void caseList() throws IOException {
		Codec<List<Cases>> codec = l.get(new TypeToken<>() {});

		Cases.D v1 = Cases.D.of();
		Cases.C v2 = Cases.C.of("Z");
		Cases.A v3 = new Cases.A.Builder().a(42).build();

		String json = toJson(codec, ImmutableList.of(v1, v2, v3));

		that(codec.decode(in(json))).isOf(v1, v2, v3);
	}

	@Test
	public void okToJson() {
		OkJson ok = new OkJson();

		that(ok.toJson(Map.of("a", 1), new TypeToken<Map<String, Integer>>() {})).is("{\"a\":1}");
		that(ok.toJson("abc")).is("\"abc\"");
	}

	@Test
	public void okFromJson() {
		OkJson ok = new OkJson();

		that(ok.fromJson("12", Integer.class)).is(12);
		that(ok.fromJson("[1,2,3]", new TypeToken<List<Long>>() {})).isOf(1L, 2L, 3L);
	}

	public static Codec.In in(CharSequence chars) {
		Buffer buffer = new Buffer();
		buffer.writeUtf8(chars.toString());
		JsonReader reader = JsonReader.of(buffer);
		reader.setLenient(true);
		return OkJson.in(reader);
	}

	private <T> String toJson(Codec<T> codec, T instance) throws IOException {
		codec.encode(OkJson.out(JsonWriter.of(buffer)), instance);
		return buffer.readUtf8().replace('"', '\'');
	}
}
