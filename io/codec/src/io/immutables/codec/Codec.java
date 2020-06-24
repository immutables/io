package io.immutables.codec;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.reflect.TypeToken;
import io.immutables.Nullable;
import java.io.IOException;
import java.lang.annotation.Target;
import java.util.function.Supplier;
import static java.lang.annotation.ElementType.TYPE_USE;

public abstract class Codec<T> {
	public abstract T decode(In in) throws IOException;
	public abstract void encode(Out out, T instance) throws IOException;

	public enum At {
		NULL,
		INT,
		LONG,
		DOUBLE,
		BOOLEAN,
		STRING,
		STRUCT,
		STRUCT_END,
		FIELD,
		ARRAY,
		ARRAY_END,
		EOF,
	}

	public interface Out extends Err, Adapter {
		void putInt(int i) throws IOException;
		void putLong(long l) throws IOException;
		void putDouble(double d) throws IOException;
		void putBoolean(boolean b) throws IOException;
		void putSpecial(Object o) throws IOException;
		void putNull() throws IOException;
		void putString(CharSequence s) throws IOException;
		void endArray() throws IOException;
		void beginArray() throws IOException;
		void beginStruct(FieldIndex f) throws IOException;
		void putField(@Field int field) throws IOException;
		void endStruct() throws IOException;
	}

	public interface In extends Err, Adapter {
		At peek() throws IOException;
		int takeInt() throws IOException;
		long takeLong() throws IOException;
		double takeDouble() throws IOException;
		boolean takeBoolean() throws IOException;
		void takeNull() throws IOException;
		void skip() throws IOException;
		CharSequence takeString() throws IOException;
		Object takeSpecial() throws IOException;
		boolean hasNext() throws IOException;
		void beginArray() throws IOException;
		void endArray() throws IOException;
		void beginStruct(FieldIndex f) throws IOException;
		@Field
		int takeField() throws IOException;
		void endStruct() throws IOException;
	}

	public interface Err {
		String getPath();

		/** Emits error if condition is false. */
		void expect(boolean condition, Supplier<String> message) throws IOException;

		default void unexpected(String message) throws IOException {
			expect(false, () -> message);
		}
	}

	public interface Adapter {
		@Nullable
		Object adapts();
	}

	public Codec<T> toNullable() {
		if (this instanceof NullAware && ((NullAware) this).supportsNull()) {
			return this;
		}
		return new NullableCodec<>(this);
	}

	private static class NullableCodec<T> extends Codec<T> implements NullAware {
		private final Codec<T> strict;

		NullableCodec(Codec<T> strict) {
			this.strict = strict;
		}

		@Override
		public boolean supportsNull() {
			return true;
		}

		@Override
		public T decode(In in) throws IOException {
			if (in.peek() == At.NULL) {
				in.takeNull();
				return null;
			}
			return strict.decode(in);
		}

		@Override
		public void encode(Out out, T instance) throws IOException {
			if (instance == null) out.putNull();
			strict.encode(out, instance);
		}

		@Override
		public String toString() {
			return strict + "(nullable)";
		}
	}

	// Experimental
	public static abstract class ContainerCodec<E, T> extends Codec<T> {
		public abstract Codec<E> element();
		public abstract <R, K> ContainerCodec<R, K> withElement(Codec<R> element);
	}

	/**
	 * Sub interface that allows codes to communicate that they already handles null well. Because
	 * some of the codecs may be configured to support {@code null} or not it is not enought to check
	 * for the
	 * {@code instanceof NullAware}, but also to check if {@link #supportsNull()} is actually on by
	 * this particual codec instance.
	 */
	interface NullAware {
		boolean supportsNull();
	}

	/**
	 * Codecs are not registered independently, but by using factories. Factories can provide either
	 * statically known codecs or dynamically built ones depending on requested type (and qualifier).
	 */
	public interface Factory {
		@Nullable
		<T> Codec<T> get(Resolver lookup, TypeToken<T> type);
	}

	@Target(TYPE_USE)
	public @interface Field {}

	public interface FieldIndex {
		@Field
		int nameToIndex(CharSequence name);
		CharSequence indexToName(@Field int field);

		int count();

		default boolean isDynamic() {
			return true;
		}

		void put(Object o);

		@Nullable
		Object get();
	}

	public static FieldIndex knownFields(String... fields) {
		return new FieldIndex() {
			private final BiMap<String, Integer> order = HashBiMap.create();
			{
				for (int i = 0; i < fields.length; i++) {
					order.put(fields[i], i);
				}
			}
			private @Nullable Object cache;

			@Override
			public @Field int nameToIndex(CharSequence name) {
				// FIXME is auto-grow is a correct way to handle unknown fields
				// and pass them over while piping etc
				// maybe create incrementing negative indeces?
				Integer nn = order.computeIfAbsent(name.toString(), s -> order.size());
				return nn;
			}

			@Override
			public CharSequence indexToName(@Field int field) {
				String nn = order.inverse().getOrDefault(field, "#" + field);
				return nn;
			}

			@Override
			public int count() {
				return order.size();
			}

			@Override
			public boolean isDynamic() {
				return false;
			}

			@Override
			public void put(Object cache) {
				this.cache = cache;
			}

			@Override
			public @Nullable Object get() {
				return cache;
			}
			@Override
			public String toString() {
				return "Codec.knownFields(" + String.join(", ", fields) + ")";
			}
		};
	}

	public static FieldIndex arbitraryFields() {
		return new FieldIndex() {
			private final BiMap<String, Integer> order = HashBiMap.create();

			@Override
			public @Field int nameToIndex(CharSequence name) {
				Integer nn = order.computeIfAbsent(name.toString(), s -> order.size());
				return nn;
			}

			@Override
			public CharSequence indexToName(@Field int field) {
				String nn = order.inverse().getOrDefault(field, "#" + field);
				return nn;
			}

			@Override
			public int count() {
				return order.size();
			}

			@Override
			public void put(Object obj) {}

			@Override
			public @Nullable Object get() {
				return null;
			}
		};
	}
}
