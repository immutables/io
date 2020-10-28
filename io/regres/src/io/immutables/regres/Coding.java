package io.immutables.regres;

import io.immutables.Unreachable;
import io.immutables.codec.Codec;
import io.immutables.codec.Codec.*;
import io.immutables.codec.OkJson;
import io.immutables.codec.Resolver;
import okio.Buffer;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.google.common.reflect.TypeToken;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;

/**
 * Binds data to Prepared Statements and from ResultSet.
 */
final class Coding {
	private Coding() {}

  static Resolver wrap(Resolver resolver) {
	  return new Resolver() {
      @Override public <T> Codec<T> get(TypeToken<T> type, Annotation qualifier) {
        Class<? super T> t = type.getRawType();
 //       TODO OPTIMIZE Builtin types ?
 //       if (qualifier == null && type == )
        Codec<T> delegate = resolver.get(type, qualifier);
        if (t == Instant.class && qualifier == null) {
          return new Codec<T>() {
            @Override public T decode(In in) throws IOException {
              return delegate.decode(in);
            }

            @Override public void encode(Out out, T instance) throws IOException {
              if (out instanceof StatementParameterOut) {
                out.putSpecial(instance);
              } else {
                delegate.encode(out, instance);
              }
            }
          };
        }
        return delegate;
      }
    };
  }

  static final class ResultSetIn implements In {
		private final ResultSet results;
		private final int columnCount;
		private final String[] names;
		private final Object[] values;
		private final @Field int[] indexes;

		private At peek = At.ARRAY;
		private int atRow = -1;
		private int atColumn = -1;

		// Will not close result set
		ResultSetIn(ResultSet results) throws SQLException {
			this.results = results;
			ResultSetMetaData meta = results.getMetaData();
			columnCount = meta.getColumnCount();

			names = new String[columnCount];
			values = new Object[columnCount];
			indexes = new int[columnCount];
			Arrays.fill(indexes, -1);

			for (int i = 0; i < columnCount; i++) {
				names[i] = meta.getColumnLabel(i + 1);
			}
		}

		private FieldIndex fieldIndex;

		@Override
		public Object adapts() {
			return results;
		}

		@Override
		public At peek() throws IOException {
			return peek;
		}

		@Override
		public String getPath() {
			return "$" + (atRow >= 0 ? atRow : "") + (atColumn >= 0 ? "." + names[atColumn] : "");
		}

		@Override
		public void endArray() throws IOException {
			expect(peek == At.ARRAY_END, () -> "not at the end of result set");
			peek = At.EOF;
		}

		@Override
		public void beginArray() throws IOException {
			expect(peek == At.ARRAY, () -> "not at beginning of result set");
			advanceRow();
		}

		@Override
		public void beginStruct(FieldIndex fieldIndex) throws IOException {
			expect(peek == At.STRUCT, () -> "not at beginning of result set");
			if (this.fieldIndex != fieldIndex || fieldIndex.isDynamic()) {
				this.fieldIndex = fieldIndex;
				for (int i = 0; i < columnCount; i++) {
					indexes[i] = fieldIndex.nameToIndex(names[i]);
				}
			}
			advanceColumn();
		}

		private void advanceColumn() {
			atColumn++;
			if (atColumn >= columnCount) {
				atColumn = -1;
				peek = At.STRUCT_END;
			} else {
				peek = At.FIELD;
			}
		}

		private void typeValue() {
			Object v = values[atColumn];
			if (v == null) {
				peek = At.NULL;
			} else if (v instanceof Long) {
				peek = At.LONG;
			} else if (v instanceof Integer) {
				peek = At.INT;
			} else if (v instanceof Number) {
				peek = At.DOUBLE;
			} else if (v instanceof Boolean) {
				peek = At.LONG;
			} else { // if (v instanceof String) // WHAT ABOUT OTHER/SPECIAL?
				peek = At.STRING;
			}
		}

		@Override
		public @Field int takeField() throws IOException {
			expect(peek == At.FIELD, () -> "not at column");
			typeValue();
			return indexes[atColumn];
		}

		@Override
		public void endStruct() throws IOException {
			expect(peek == At.STRUCT_END, () -> "not at the end of the row");
			advanceRow();
		}

		private void advanceRow() {
			atColumn = -1;
			try {
				if (!results.next()) {
					peek = At.ARRAY_END;
				} else {
					atRow++;
					peek = At.STRUCT;
					for (int i = 0; i < columnCount; i++) {
						values[i] = results.getObject(i + 1);
					}
				}
			} catch (SQLException ex) {
				Unreachable.<RuntimeException>uncheckedThrow(ex);
			}
		}

		@Override
		public CharSequence takeString() throws IOException {
			String s = String.valueOf(values[atColumn]);
			advanceColumn();
			return s;
		}

		@Override
		public Object takeSpecial() throws IOException {
			Object v = values[atColumn];
			advanceColumn();
			return v;
		}

		@Override
		public void takeNull() throws IOException {
			expect(peek == At.NULL, () -> "not at null value");
			advanceColumn();
		}

		@Override
		public long takeLong() throws IOException {
			long l;
			switch (peek) { // @formatter:off
			case INT: //$FALL-THROUGH$
			case LONG: //$FALL-THROUGH$
			case DOUBLE: l = ((Number)values[atColumn]).longValue(); break;
			case STRING: l = Long.parseLong((String)values[atColumn]); break;
			default: unexpected("not at int value"); l = 0;
			} // @formatter:on
			advanceColumn();
			return l;
		}

		@Override
		public int takeInt() throws IOException {
			int i;
			switch (peek) { // @formatter:off
			case INT: //$FALL-THROUGH$
			case LONG: //$FALL-THROUGH$
			case DOUBLE: i = ((Number)values[atColumn]).intValue(); break;
			case STRING: i = Integer.parseInt((String)values[atColumn]); break;
			default: unexpected("not at int value"); i = 0;
			} // @formatter:on
			advanceColumn();
			return i;
		}

		@Override
		public double takeDouble() throws IOException {
			double d;
			switch (peek) { // @formatter:off
			case INT: //$FALL-THROUGH$
			case LONG: //$FALL-THROUGH$
			case DOUBLE: d = ((Number)values[atColumn]).doubleValue(); break;
			case STRING: d = Double.parseDouble((String)values[atColumn]); break;
			default: unexpected("not at double value"); d = 0.0;
			} // @formatter:on
			advanceColumn();
			return d;
		}

		@Override
		public boolean takeBoolean() throws IOException {
			boolean b;
			switch (peek) { // @formatter:off
			case BOOLEAN: b = (boolean) values[atColumn]; break;
			case INT: //$FALL-THROUGH$
			case LONG: //$FALL-THROUGH$
			case DOUBLE: b = ((Number)values[atColumn]).intValue() != 0; break;
			case STRING: b = Boolean.parseBoolean((String)values[atColumn]); break;
			default: b = values[atColumn] != null;
			} // @formatter:on
			advanceColumn();
			return b;
		}

		@Override
		public void skip() throws IOException {
			if (peek == At.STRUCT) {
				advanceRow();
			} else if (peek == At.ARRAY) {
				peek = At.EOF;
			} else {
				advanceColumn();
			}
		}

		@Override
		public boolean hasNext() throws IOException {
			return peek == At.FIELD // field (column) within struct (row)
					|| peek == At.STRUCT; // struct (row) within array (of results)
		}

		@Override
		public void expect(boolean condition, Supplier<String> message) throws IOException {
			// TODO is anything smarter or better we can do here?
			if (!condition) throw new IOException(message.get());
		}
	}

	static class StatementParameterOut implements Out {
		private static final Object MASKED_NULL = new Object();
		private enum SpreadState {
			EXPECT, DOING, NONE
		}
		private final FieldIndex parameterIndex;
		private final Map<String, Object> values = new HashMap<>();
		private SpreadState spreading = SpreadState.NONE;
		private String prefix = "";
		private String field = "";
		private FieldIndex spreadingIndex;

		StatementParameterOut(FieldIndex parameterIndex) {
			this.parameterIndex = parameterIndex;
		}

		Object get(String name) throws IOException {
			Object v = values.get(name);
			if (v == null) unexpected("No value for placeholder :" + name);
			if (v == MASKED_NULL) return null;
			return v;
		}

		void spread(String prefix) {
			spreading = SpreadState.EXPECT;
			this.prefix = prefix;
		}

		@Override
		public void beginStruct(FieldIndex f) throws IOException {
			if (spreading == SpreadState.EXPECT) {
				spreading = SpreadState.DOING;
				spreadingIndex = f;
			} else {
				unexpected("Parameter at " + getPath() + " uses nested structure. Instead use @Spread or JSONB conversion");
			}
		}

		@Override
		public void endStruct() throws IOException {
			if (spreading == SpreadState.DOING) {
				spreading = SpreadState.NONE;
				prefix = "";
			} else {
				unexpected("Out of order end of struct at " + getPath());
			}
		}

		private FieldIndex fields() {
			return spreading == SpreadState.DOING ? spreadingIndex : parameterIndex;
		}

		@Override
		public void putField(@Field int field) throws IOException {
			this.field = prefix + fields().indexToName(field);
		}

		@Override
		public void endArray() throws IOException {
			unexpected("Out of order end of array at " + getPath());
		}

		@Override
		public void beginArray() throws IOException {
			unexpected("Parameter at " + getPath() + " uses nested array. Instead use JSONB conversion.");
		}

		@Override
		public void putInt(int i) throws IOException {
			values.put(field, i);
		}

		@Override
		public void putLong(long l) throws IOException {
			values.put(field, l);
		}

		@Override
		public void putDouble(double d) throws IOException {
			values.put(field, d);
		}

		@Override
		public void putBoolean(boolean b) throws IOException {
			values.put(field, b);
		}

		@Override
		public void putSpecial(Object o) throws IOException {
			values.put(field, o);
		}

		@Override
		public void putNull() throws IOException {
			values.put(field, MASKED_NULL);
		}

		@Override
		public void putString(CharSequence s) throws IOException {
			values.put(field, s.toString());
		}

		@Override
		public void expect(boolean condition, Supplier<String> message) throws IOException {
			// TODO is anything smarter or better we can do here?
			if (!condition) throw new IOException(message.get());
		}

		@Override
		public String getPath() {
			return field;
		}

		@Override
		public Object adapts() {
			return values;
		}
	}

	static class SingleRowDecoder extends Codec<Object> {
		private final Codec<Object> codec;
		private final SqlAccessor.Single single;

		SingleRowDecoder(Codec<Object> codec, SqlAccessor.Single single) {
			this.codec = codec;
			this.single = single;
		}

		@Override
		public Object decode(In in) throws IOException {
			Object returnValue = null;
			in.beginArray();

			if (in.hasNext()) {
				returnValue = codec.decode(in);

				if (in.hasNext()) {
					if (!single.ignoreMore()) throw new IOException(
							"More than one row available, use @Single.ignoreMore=true to skip the rest");
					do {
						in.skip();
					} while (in.hasNext());
				}
			} else {
				if (!single.optional()) throw new IOException(
						"Exactly one row expected as result, was none. Use @Single.optional=true to allow no results.");

				returnValue = codec.toNullable().decode(nullIn());
			}

			in.endArray();
			return returnValue;
		}

		@Override
		public void encode(Out out, Object instance) {
			throw new UnsupportedOperationException();
		}

		private static In nullIn() throws IOException {
			// is there more efficient way? implement own TokenBuffer?
			return OkJson.in(JsonReader.of(new Buffer().writeUtf8("null")));
		}
	}

	static class ColumnExtractor extends Codec<Object> {
		private final Codec<Object> codec;
    private final SqlAccessor.Column column;

    ColumnExtractor(Codec<Object> codec, SqlAccessor.Column column) {
			this.codec = codec;
      this.column = column;
    }

		@Override
		public Object decode(In in) throws IOException {
			Object returnValue = null;
			boolean columnMatched = false;
			int index = column.index();
			String name = column.value();
			boolean matchIndex = name.isEmpty();

			FieldIndex fields = Codec.arbitraryFields();
			in.beginStruct(fields);

			while (in.hasNext()) {
				@Field int f = in.takeField();
				if (columnMatched) {
					in.skip();
					continue;
				}
				if (matchIndex) {
					if (f == index) {
						returnValue = codec.decode(in);
						columnMatched = true;
					} else in.skip();
				} else {
					if (name.contentEquals(fields.indexToName(f))) {
						returnValue = codec.decode(in);
						columnMatched = true;
					} else in.skip();
				}
			}

			if (!columnMatched) throw new IOException("No column matched for " + column);

			in.endStruct();
			return returnValue;
		}

		@Override
		public void encode(Out out, Object instance) {
			throw new UnsupportedOperationException();
		}
	}
}
