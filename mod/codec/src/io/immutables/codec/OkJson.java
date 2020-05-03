package io.immutables.codec;

import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonReader.Token;
import com.squareup.moshi.JsonWriter;
import io.immutables.Capacity;
import io.immutables.Unreachable;
import io.immutables.codec.Codec.At;
import io.immutables.codec.Codec.Field;
import io.immutables.codec.Codec.FieldMapper;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import okio.Buffer;

public final class OkJson {
	private OkJson() {}

	public static Codec.In in(CharSequence chars) {
		Buffer buffer = new Buffer();
		buffer.writeUtf8(chars.toString());
		return in(JsonReader.of(buffer));
	}

	public static Codec.In in(JsonReader reader) {
		return new FromJsonReader(reader);
	}

	public static Codec.Out out(JsonWriter writer) {
		return new ToJsonWriter(writer);
	}

	private static abstract class IoBase implements Codec.Err {
		private FieldMapper[] mappersStack = new FieldMapper[4];
		private int mapperCount;

		final FieldMapper topMapper() {
			return mappersStack[mapperCount - 1];
		}

		final void pushMapper(FieldMapper mapper) {
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

		FromJsonReader(JsonReader reader) {
			this.reader = reader;
		}

		@Override
		public Object unwrap() {
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
			return topMapper().nameToIndex(reader.nextName());
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
		public void beginStruct(FieldMapper mapper) throws IOException {
			reader.beginObject();
			pushMapper(Objects.requireNonNull(mapper));
		}

		@Override
		public void endStruct() throws IOException {
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
		public Object unwrap() {
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
		public void beginStruct(FieldMapper f) throws IOException {
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
}
