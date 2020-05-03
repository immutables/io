package io.immutables.codec;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Table;
import com.google.common.reflect.TypeToken;
import io.immutables.Nullable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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

	public interface Out extends Err, Wrapper {
		void putInt(int i) throws IOException;
		void putLong(long l) throws IOException;
		void putDouble(double d) throws IOException;
		void putBoolean(boolean b) throws IOException;
		void putSpecial(Object o) throws IOException;
		void putNull() throws IOException;
		void putString(CharSequence s) throws IOException;
		void endArray() throws IOException;
		void beginArray() throws IOException;
		void beginStruct(FieldMapper f) throws IOException;
		void putField(@Field int field) throws IOException;
		void endStruct() throws IOException;
	}

	public interface In extends Err, Wrapper {
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
		void beginStruct(FieldMapper f) throws IOException;
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

	public interface Wrapper {
		@Nullable
		Object unwrap();
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

	@SuppressWarnings("unchecked") // unsupported for any type
	public static <T> Codec<T> unsupported() {
		return (Codec<T>) UNSUPPORTED;
	}

	private static final Codec<?> UNSUPPORTED = new Codec<Object>() {
		@Override
		public Object decode(In in) {
			// TODO better reporting / message
			throw new UnsupportedOperationException();
		}
		@Override
		public void encode(Out out, Object instance) {
			// TODO better reporting / message
			throw new UnsupportedOperationException();
		}
		@Override
		public String toString() {
			return "Codec.unsupported()";
		}
	};

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
		<T> Codec<T> get(Lookup lookup, TypeToken<T> type);
	}

	@Target(TYPE_USE)
	@interface Field {}

	public interface FieldMapper {
		@Field
		int nameToIndex(CharSequence name);
		CharSequence indexToName(@Field int field);

		int count();

		default boolean isDynamic() {
			return true;
		}

		void put(Object obj);
		@Nullable
		Object get();
	}

	public static FieldMapper knownFields(String... fields) {
		return new FieldMapper() {
			private final BiMap<String, Integer> order = HashBiMap.create();
			private @Nullable Object cache;

			@Override
			public @Field int nameToIndex(CharSequence name) {
				return order.computeIfAbsent(name.toString(), s -> order.size());
			}

			@Override
			public CharSequence indexToName(@Field int field) {
				return order.inverse().getOrDefault(field, "#" + field);
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

	public static FieldMapper arbitraryFields() {
		return new FieldMapper() {
			private final BiMap<String, Integer> order = HashBiMap.create();

			@Override
			public @Field int nameToIndex(CharSequence name) {
				return order.computeIfAbsent(name.toString(), s -> order.size());
			}

			@Override
			public CharSequence indexToName(@Field int field) {
				return order.inverse().getOrDefault(field, "#" + field);
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

	public interface Lookup {
		<T> Codec<T> get(TypeToken<T> type, @Nullable Annotation qualifier);

		default <T> Codec<T> get(TypeToken<T> type) {
			return get(type, null);
		}

		@SuppressWarnings("unchecked")
		default <T> Codec<T> get(Class<? extends T> type) {
			return get((TypeToken<T>) TypeToken.of(type), null);
		}

		@SuppressWarnings("unchecked")
		default <T> Codec<T> get(Type type) {
			return get((TypeToken<T>) TypeToken.of(type), null);
		}
	}

	public static final class Compound implements Lookup {
		private final Map<Factory, Annotation> factories = new IdentityHashMap<>();
		private final Table<TypeToken<?>, Object, Codec<?>> memoised = HashBasedTable.create();

		public Compound add(Factory factory) {
			return add(factory, null);
		}

		public Compound add(Factory factory, @Nullable Annotation qualifier) {
			factories.put(factory, qualifier);
			return this;
		}

		private static Object qualifierKey(@Nullable Annotation qualifier) {
			return qualifier != null ? qualifier : UNQUALIFIED;
		}

		@Override
		public <T> Codec<T> get(TypeToken<T> type, @Nullable Annotation qualifier) {
			@SuppressWarnings("unchecked") Codec<T> codec =
					(Codec<T>) memoised.get(type, qualifierKey(qualifier));
			if (codec == null) {
				codec = findUnique(type, qualifier);
				memoised.put(type, qualifierKey(qualifier), codec);
			}
			return codec;
		}

		private <T> Codec<T> findUnique(TypeToken<T> type, Annotation qualifier) {
			@Nullable Set<Codec<T>> conflicting = null;
			@Nullable Codec<T> unique = null;

			for (Entry<Factory, Annotation> e : factories.entrySet()) {
				Factory f = e.getKey();
				if (Objects.equals(qualifier, e.getValue())) {
					@Nullable Codec<T> c = f.get(this, type);
					if (c != unsupported()) {
						if (unique == null) {
							unique = c;
						} else {
							if (conflicting == null) {
								conflicting = new HashSet<>();
							}
							conflicting.add(unique);
						}
					}
				}
			}
			if (conflicting != null) {
				throw new RuntimeException(
						String.format("More than one applicable adapter found for %s @%s: %s",
								type, qualifier, conflicting));
			}
			return unique != null ? unique : unsupported();
		}

		@Override
		public String toString() {
			return "Codec.Compound(" + factories.size() + " factories)";
		}

		private static final Object UNQUALIFIED = new Object();
	}
}
