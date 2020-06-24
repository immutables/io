package io.immutables.codec;

import com.google.common.reflect.TypeToken;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonReader.Options;
import com.squareup.moshi.JsonReader.Token;
import com.squareup.moshi.JsonWriter;
import io.immutables.Capacity;
import io.immutables.Unreachable;
import io.immutables.codec.Codec.At;
import io.immutables.codec.Codec.Field;
import io.immutables.codec.Codec.FieldIndex;
import io.immutables.codec.Codec.NullAware;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import okio.*;
import javax.annotation.Nullable;

public final class OkJson implements Resolver {
	private final Resolver resolver;
	public final String indent;
	public final boolean serializeNulls;
	public final boolean lenient;

	private OkJson(Resolver resolver, String indent, boolean lenient, boolean serializeNulls) {
		this.resolver = resolver;
		this.lenient = lenient;
		this.indent = indent;
		this.serializeNulls = serializeNulls;
	}

	public OkJson(Resolver resolver) {
		this(resolver, "", false, false);
	}

	public OkJson() {
		this(Codecs.builtin().toResolver(), "", false, false);
	}

	public interface Setup {
		Setup serializeNulls(boolean serializeNulls);
		Setup lenient(boolean lenient);
		Setup indent(String indent);
		Setup add(Codec.Factory factory);
		Setup add(Codec.Factory factory, @Nullable Annotation qualifier, int priority);
	}

	public static OkJson configure(Consumer<Setup> c) {
		var configurer = new Setup() {
			String indent = "";
			Resolver.Compound compound = Codecs.builtin();
			boolean serializeNulls;
			boolean lenient;

			@Override public Setup serializeNulls(boolean serializeNulls) {
				this.serializeNulls = serializeNulls;
				return this;
			}

			@Override
			public Setup lenient(boolean lenient) {
				this.lenient = lenient;
				return this;
			}

			@Override
			public Setup indent(String indent) {
				this.indent = indent;
				return this;
			}

			@Override
			public Setup add(Codec.Factory factory) {
				compound.add(factory);
				return this;
			}

			@Override
			public Setup add(Codec.Factory factory, @Nullable Annotation qualifier, int priority) {
				compound.add(factory, qualifier, priority);
				return this;
			}
		};

		c.accept(configurer);

		return new OkJson(
				configurer.compound.toResolver(),
				configurer.indent,
				configurer.lenient,
				configurer.serializeNulls);
	}

	@Override
	public <T> Codec<T> get(TypeToken<T> type, Annotation qualifier) {
		return resolver.get(type, qualifier);
	}

	public <T> T fromJson(String json, Class<T> type) {
		return fromJson(new Buffer().writeUtf8(json), get(type));
	}

	public <T> T fromJson(String json, TypeToken<T> type) {
		return fromJson(new Buffer().writeUtf8(json), get(type));
	}

	public <T> T fromJson(String json, Codec<T> codec) {
		return fromJson(new Buffer().writeUtf8(json), codec);
	}

	public String toJson(Object instance) {
		Buffer buffer = new Buffer();
		toJson(instance, buffer, get(instance.getClass()));
		return buffer.readUtf8();
	}

	public <T> String toJson(T instance, TypeToken<T> type) {
		Buffer buffer = new Buffer();
		toJson(instance, buffer, get(type));
		return buffer.readUtf8();
	}

	public <T> String toJson(T instance, Class<T> type) {
		Buffer buffer = new Buffer();
		toJson(instance, buffer, get(type));
		return buffer.readUtf8();
	}

	public <T> String toJson(T instance, Codec<T> codec) {
		Buffer buffer = new Buffer();
		toJson(instance, buffer, codec);
		return buffer.readUtf8();
	}

	public <T> void toJson(T instance, BufferedSink sink, Codec<T> codec) {
		try {
			JsonWriter writer = JsonWriter.of(sink);
			init(writer);
			codec.encode(out(writer), instance);
			writer.flush();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public <T> T fromJson(BufferedSource source, Codec<T> codec) {
		try {
			JsonReader reader = JsonReader.of(source);
			init(reader);
			return codec.decode(in(reader));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	void init(JsonReader reader) {
		reader.setLenient(lenient);
	}

	void init(JsonWriter writer) {
		writer.setIndent(indent);
		writer.setLenient(lenient);
		writer.setSerializeNulls(serializeNulls);
	}

	public static Codec.In in(JsonReader reader) {
		return new FromJsonReader(reader);
	}

	public static Codec.Out out(JsonWriter writer) {
		return new ToJsonWriter(writer);
	}

	private static abstract class IoBase implements Codec.Err {
		private FieldIndex[] mappersStack = new FieldIndex[4];
		private int mapperCount;

		final FieldIndex topMapper() {
			return mappersStack[mapperCount - 1];
		}

		final void pushMapper(FieldIndex mapper) {
			mappersStack = Capacity.ensure(mappersStack, mapperCount, 1);
			mappersStack[mapperCount++] = mapper;
		}

		final void popMapper() {
			mappersStack[--mapperCount] = null;
		}

		@Override
		public void expect(boolean condition, Supplier<String> message) throws IOException {
			if (!condition) throw new IOException(message.get());
		}
	}

	private static final class FromJsonReader extends IoBase implements Codec.In {
		private final JsonReader reader;
		private Options options;

		FromJsonReader(JsonReader reader) {
			this.reader = reader;
		}

		@Override
		public Object adapts() {
			return reader;
		}

		@Override
		public String getPath() {
			return reader.getPath();
		}

		@Override
		public CharSequence takeString() throws IOException {
			return reader.nextString();
		}

		@Override
		public void takeNull() throws IOException {
			reader.nextNull();
		}

		@Override
		public long takeLong() throws IOException {
			return reader.nextLong();
		}

		@Override
		public int takeInt() throws IOException {
			return reader.nextInt();
		}

		@Override
		public Object takeSpecial() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public @Field int takeField() throws IOException {
			if (options != null) {
				int knownField = reader.selectName(this.options);
				if (knownField >= 0) return knownField;
			}
			String name = reader.nextName();
			int inx = topMapper().nameToIndex(name);
			if (inx == -1) {
				System.err.println("GRrrr!! " + inx);
			}
			return inx;
		}

		@Override
		public double takeDouble() throws IOException {
			return reader.nextDouble();
		}

		@Override
		public boolean takeBoolean() throws IOException {
			return reader.nextBoolean();
		}

		@Override
		public void skip() throws IOException {
			reader.skipValue();
		}

		@Override
		public At peek() throws IOException {
			return atToken(reader.peek());
		}

		@Override
		public boolean hasNext() throws IOException {
			return reader.hasNext();
		}

		@Override
		public void endArray() throws IOException {
			reader.endArray();
		}

		@Override
		public void beginArray() throws IOException {
			reader.beginArray();
		}

		@Override
		public void beginStruct(FieldIndex mapper) throws IOException {
			reader.beginObject();
			pushMapper(Objects.requireNonNull(mapper));

			options = null; // clear paranoia
			if (!mapper.isDynamic()) {
				Object cached = mapper.get();
				if (cached == null) {
					int count = mapper.count();
					String[] knownFields = new String[count];
					for (int i = 0; i < count; i++) {
						knownFields[i] = mapper.indexToName(i).toString();
					}
					options = Options.of(knownFields);
					mapper.put(options);
				} else if (cached instanceof Options) {
					options = (Options) cached;
				} // else this is not our business if cached is smth
			}
		}

		@Override
		public void endStruct() throws IOException {
			options = null;
			popMapper();
			reader.endObject();
		}
	}

	private static final class ToJsonWriter extends IoBase implements Codec.Out {

		private final JsonWriter writer;

		ToJsonWriter(JsonWriter writer) {
			this.writer = writer;
		}

		@Override
		public String getPath() {
			return writer.getPath();
		}

		@Override
		public Object adapts() {
			return writer;
		}

		@Override
		public void putInt(int i) throws IOException {
			writer.value(i);
		}

		@Override
		public void putLong(long l) throws IOException {
			writer.value(l);
		}

		@Override
		public void putDouble(double d) throws IOException {
			writer.value(d);
		}

		@Override
		public void putBoolean(boolean b) throws IOException {
			writer.value(b);
		}

		@Override
		public void putSpecial(Object o) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void putNull() throws IOException {
			writer.nullValue();
		}

		@Override
		public void putString(CharSequence s) throws IOException {
			writer.value(s.toString());
		}

		@Override
		public void endArray() throws IOException {
			writer.endArray();
		}

		@Override
		public void beginArray() throws IOException {
			writer.beginArray();
		}

		@Override
		public void putField(@Field int field) throws IOException {
			writer.name(topMapper().indexToName(field).toString());
		}

		@Override
		public void beginStruct(FieldIndex f) throws IOException {
			writer.beginObject();
			pushMapper(f);
		}

		@Override
		public void endStruct() throws IOException {
			popMapper();
			writer.endObject();
		}
	}

	private static At atToken(Token t) {
		switch (t) { // @formatter:off
		case BEGIN_ARRAY: return At.ARRAY;
		case END_ARRAY: return At.ARRAY_END;
		case BEGIN_OBJECT: return At.STRUCT;
		case END_OBJECT: return At.STRUCT_END;
		case NAME: return At.FIELD;
		case STRING: return At.STRING;
		case NUMBER: return At.DOUBLE;
		case BOOLEAN: return At.BOOLEAN;
		case NULL: return At.NULL;
		case END_DOCUMENT: return At.EOF;
		default: throw Unreachable.exhaustive();
		} // @formatter:on
	}

	public final static class JsonStringFactory implements Codec.Factory {
		private final String indent;

		public JsonStringFactory() {
			this("");
		}

		public JsonStringFactory(String indent) {
			this.indent = indent;
		}

		// ! this factory should only be used in qualified form
		@Override
		public <T> Codec<T> get(Resolver lookup, TypeToken<T> type) {
			return new JsonCodec<>(lookup.get(type), indent, false);
		}

		private static class JsonCodec<T> extends Codec<T> implements NullAware {
			private final Codec<T> original;
			private final boolean supportsNull;
			private final String indent;

			JsonCodec(Codec<T> original, String indent, boolean supportsNull) {
				this.original = original;
				this.indent = indent;
				this.supportsNull = supportsNull;
			}

			@Override
			public T decode(In in) throws IOException {
				CharSequence c = in.takeString();
				Buffer buffer = new Buffer();
				buffer.writeUtf8(c.toString());
				return original.decode(in(JsonReader.of(buffer)));
			}

			@Override
			public void encode(Out out, T instance) throws IOException {
				Buffer buffer = new Buffer();
				JsonWriter writer = JsonWriter.of(buffer);
				writer.setIndent(indent);
				original.encode(out(writer), instance);
				out.putString(buffer.readUtf8());
			}

			@Override
			public boolean supportsNull() {
				return supportsNull;
			}

			@Override
			public Codec<T> toNullable() {
				if (supportsNull) return this;
				return new JsonCodec<>(original.toNullable(), indent, true);
			}

			@Override
			public String toString() {
				return "json for " + original;
			}
		}
	}
}
