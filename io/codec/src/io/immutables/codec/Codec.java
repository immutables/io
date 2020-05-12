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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

	@SuppressWarnings("unchecked") // unsupported for any type
	public static <T> Codec<T> unsupported(TypeToken<T> type, @Nullable Annotation qualifier) {
		return new Codec<T>() {
			@Override
			public T decode(In in) {
				// TODO better reporting / message
				throw new UnsupportedOperationException();
			}
			@Override
			public void encode(Out out, T instance) {
				// TODO better reporting / message
				throw new UnsupportedOperationException();
			}
			@Override
			public String toString() {
				return "Codec.unsupported(" + type + (qualifier != null ? " @" + qualifier : "") + ")";
			}
		};
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

	// The opposite of order, the bigger - the higher priority.
	@Target(TYPE_USE)
	public @interface Priority {}

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

	public static FieldIndex arbitraryFields() {
		return new FieldIndex() {
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

	public interface Resolver {
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

	public static final class Compound implements Resolver {
		static final @Priority int DEFAULT_PRIORITY = 0;
		static final @Priority int LOWEST_PRIORITY = Integer.MIN_VALUE;

		private final List<FactoryEntry> factories = new ArrayList<>();
		private final Table<TypeToken<?>, Object, Codec<?>> memoised = HashBasedTable.create();

		private static class FactoryEntry implements Comparable<FactoryEntry> {
			Factory factory;
			@Nullable
			Annotation qualifier;
			@Priority
			int priority = DEFAULT_PRIORITY;

			@Override
			public int compareTo(FactoryEntry o) {
				return o.priority - priority;
			}
		}

		public Compound add(Factory factory) {
			return add(factory, null, DEFAULT_PRIORITY);
		}

		public Compound add(Factory factory, @Nullable Annotation qualifier, @Priority int priority) {
			FactoryEntry e = new FactoryEntry();
			e.factory = factory;
			e.qualifier = qualifier;
			e.priority = priority;
			factories.add(e);
			Collections.sort(factories);
			return this;
		}

		private static Object qualifierKey(@Nullable Annotation qualifier) {
			return qualifier != null ? qualifier : UNQUALIFIED;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> Codec<T> get(TypeToken<T> type, @Nullable Annotation qualifier) {
			// (type.getType())
			Codec<T> codec = (Codec<T>) memoised.get(type, qualifierKey(qualifier));
			if (codec == null) {
				codec = findBestUncontested(type, qualifier);
				memoised.put(type, qualifierKey(qualifier), codec);
			}
			return codec;
		}

		@SuppressWarnings("null") // cannot be null, bestEntry assigned with best
		private <T> Codec<T> findBestUncontested(TypeToken<T> type, @Nullable Annotation qualifier) {
			@Nullable List<FactoryEntry> contesters = null;
			@Nullable FactoryEntry bestEntry = null;
			@Nullable Codec<T> best = null;

			for (FactoryEntry e : factories) {
				// short circuit search if we already found something and priority is lower now
				if (bestEntry != null && bestEntry.priority > e.priority) break;

				Factory f = e.factory;
				if (Objects.equals(qualifier, e.qualifier)) {
					@Nullable Codec<T> c = f.get(this, type);
					if (c != null) {
						if (best != null) {
							assert bestEntry != null;
							assert bestEntry.priority == e.priority;
							if (contesters == null) {
								contesters = new ArrayList<>(2);
								contesters.add(bestEntry);
							}
							contesters.add(e);
						} else {
							best = c;
							bestEntry = e;
						}
					}
				}
			}
			if (contesters != null) {
				assert bestEntry != null;
				throw new RuntimeException(
						String.format("More than one applicable adapter founds for %s @%s. Factories with priority %s: %s",
								type, qualifier, bestEntry.priority, contesters));
			}
			return best != null ? best : unsupported(type, qualifier);
		}

		@Override
		public String toString() {
			return "Codec.Compound(" + factories.size() + " factories)";
		}

		private static final Object UNQUALIFIED = new Object();
	}
}
