package io.immutables.codec;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import io.immutables.Nullable;
import io.immutables.Unreachable;
import io.immutables.codec.Codec.At;
import io.immutables.codec.Codec.Compound;
import io.immutables.codec.Codec.ContainerCodec;
import io.immutables.codec.Codec.Field;
import io.immutables.codec.Codec.FieldIndex;
import io.immutables.codec.Codec.In;
import io.immutables.codec.Codec.NullAware;
import io.immutables.codec.Codec.Out;
import io.immutables.codec.Codec.Resolver;
import io.immutables.collect.Vect;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import org.immutables.data.Datatype;

public final class Codecs {
	private Codecs() {}

	public static @Nullable Annotation findQualifier(AnnotatedElement element) {
		@Nullable Annotation found = null;
		for (Annotation a : element.getAnnotations()) {
			if (a.annotationType().isAnnotationPresent(CodecQualifier.class)) {
				if (found != null) {
					throw new IllegalArgumentException(
							element + " has more than one annotation (@CodecQualifier): " + found + ", " + a);
				}
				found = a;
			}
		}
		return found;
	}

	public static Codec.Compound builtin() {
		return new Codec.Compound()
				.add(SCALARS, null, Compound.LOWEST_PRIORITY + 1)
				.add(ENUMS, null, Compound.LOWEST_PRIORITY + 1)
				.add(COLLECTIONS, null, Compound.LOWEST_PRIORITY + 1)
				.add(OPTIONALS, null, Compound.LOWEST_PRIORITY + 1)
				.add(DATATYPES, null, Compound.LOWEST_PRIORITY);
	}

	private static Codec.Factory DATATYPES = new Codec.Factory() {
		@SuppressWarnings("unchecked")
		@Override
		public @Nullable <T> Codec<T> get(Resolver lookup, TypeToken<T> type) {
			@Nullable Datatype<T> t = null;
			try {
				t = Datatypes.construct(type);
			} catch (Exception cannotConstructDatatype) {
				cannotConstructDatatype.printStackTrace();
				return null;
			}

			return t != null ? new DatatypeCodec<>(t, lookup) : null;
		}
		@Override
		public String toString() {
			return "Codec.Factory for Datatypes";
		}
	};

	private static Codec.Factory ENUMS = new Codec.Factory() {
		@SuppressWarnings("unchecked")
		@Override
		public @Nullable <T> Codec<T> get(Resolver lookup, TypeToken<T> type) {
			Class<?> c = type.getRawType();
			if (c.isEnum()) {
				return new EnumCodec<>((Class<T>) c, o -> ((Enum<?>) o).name(), false);
			}
			return null;
		}
		@Override
		public String toString() {
			return "Codec.Factory for enums";
		}
	};

	@FunctionalInterface
	interface CollectionConstructor {
		<E, C> C construct(Iterable<E> elements);
	}

	private static Codec.Factory COLLECTIONS = new Codec.Factory() {
		private final Type listTypeParameter = List.class.getTypeParameters()[0];
		private final Type setTypeParameter = Set.class.getTypeParameters()[0];
		private final Type vectTypeParameter = Vect.class.getTypeParameters()[0];
		private final Type mapKeyTypeParameter = Map.class.getTypeParameters()[0];
		private final Type mapValueTypeParameter = Map.class.getTypeParameters()[1];

		private final CollectionConstructor listConstructor = new CollectionConstructor() {
			@SuppressWarnings("unchecked")
			@Override
			public <E, C> C construct(Iterable<E> elements) {
				return (C) ImmutableList.copyOf(elements);
			}
		};

		private final CollectionConstructor setConstructor = new CollectionConstructor() {
			@SuppressWarnings("unchecked")
			@Override
			public <E, C> C construct(Iterable<E> elements) {
				return (C) ImmutableSet.copyOf(elements);
			}
		};

		private final CollectionConstructor vectConstructor = new CollectionConstructor() {
			@SuppressWarnings("unchecked")
			@Override
			public <E, C> C construct(Iterable<E> elements) {
				return (C) Vect.from(elements);
			}
		};

		@SuppressWarnings("unchecked") // runtime token + checks
		@Override
		public @Nullable <T> Codec<T> get(Resolver lookup, TypeToken<T> type) {
			Class<?> rawType = type.getRawType();
			if (rawType == List.class || rawType == ImmutableList.class) {
				Codec<?> codec = lookup.get(type.resolveType(listTypeParameter));
				return (Codec<T>) new ArrayCodec<>((Codec<Object>) codec, listConstructor);
			}
			if (rawType == Set.class || rawType == ImmutableSet.class) {
				Codec<?> codec = lookup.get(type.resolveType(setTypeParameter));
				return (Codec<T>) new ArrayCodec<>((Codec<Object>) codec, setConstructor);
			}
			if (rawType == Vect.class) {
				Codec<?> codec = lookup.get(type.resolveType(vectTypeParameter));
				return (Codec<T>) new ArrayCodec<>((Codec<Object>) codec, vectConstructor);
			}
			if (rawType == Map.class || rawType == ImmutableMap.class) {
				Codec<?> forKey = lookup.get(type.resolveType(mapKeyTypeParameter));
				Codec<?> forValue = lookup.get(type.resolveType(mapValueTypeParameter));
				return (Codec<T>) new MapCodec<>(
						(Codec<Object>) forKey,
						(Codec<Object>) forValue,
						MapCodec.immutableMapSupplier());
			}
			return null;
		}

		@Override
		public String toString() {
			return "Codec.Factory for List<T>, Set<T>, Map<K,V>";
		}
	};

	public static class MapCodec<K, V, M extends Map<K, V>> extends Codec<M> {
		private final Codec<K> forKey;
		private final Codec<V> forValue;
		private final Supplier<MapBuilder<K, V, M>> builderSupplier;

		public MapCodec(Codec<K> forKey, Codec<V> forValue, Supplier<MapBuilder<K, V, M>> builderSupplier) {
			this.forKey = forKey;
			this.forValue = forValue;
			this.builderSupplier = builderSupplier;
		}

		@Override
		public M decode(In in) throws IOException {
			FieldIndex fields = Codec.arbitraryFields();
			in.beginStruct(fields);
			MapBuilder<K, V, M> builder = builderSupplier.get();
			StringValueIo keyIo = new StringValueIo();
			while (in.hasNext()) {
				keyIo.putString(fields.indexToName(in.takeField()));
				builder.put(forKey.decode(keyIo), forValue.decode(in));
			}
			in.endStruct();
			return builder.build();
		}

		@Override
		public void encode(Out out, M instance) throws IOException {
			FieldIndex fields = Codec.arbitraryFields();
			out.beginStruct(fields);
			StringValueIo keyIo = new StringValueIo();
			for (Entry<K, V> e : instance.entrySet()) {
				forKey.encode(keyIo, e.getKey());
				out.putField(fields.nameToIndex(keyIo.takeString()));
				forValue.encode(out, e.getValue());
			}
			out.endStruct();
		}

		public interface MapBuilder<K, V, M extends Map<K, V>> {
			void put(K k, V v);
			M build();
		}

		public static <K, V> Supplier<MapBuilder<K, V, ImmutableMap<K, V>>> immutableMapSupplier() {
			return () -> new MapCodec.MapBuilder<K, V, ImmutableMap<K, V>>() {
				final ImmutableMap.Builder<K, V> b = ImmutableMap.builder();
				@Override
				public void put(K k, V v) {
					b.put(k, v);
				}
				@Override
				public ImmutableMap<K, V> build() {
					return b.build();
				}
			};
		}
	}

	public static class ArrayCodec<E, C extends Iterable<E>> extends ContainerCodec<E, C> {
		private final Codec<E> elementCodec;
		private final CollectionConstructor constructor;

		public ArrayCodec(Codec<E> elementCodec, CollectionConstructor constructor) {
			this.elementCodec = elementCodec;
			this.constructor = constructor;
		}

		@Override
		public C decode(In in) throws IOException {
			List<E> elements = new ArrayList<>();
			in.beginArray();
			while (in.hasNext()) {
				elements.add(elementCodec.decode(in));
			}
			in.endArray();
			return constructor.construct(elements);
		}

		@Override
		public void encode(Out out, C instance) throws IOException {
			out.beginArray();
			for (E e : instance) {
				elementCodec.encode(out, e);
			}
			out.endArray();
		}

		@Override
		public Codec<E> element() {
			return elementCodec;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <R, K> ContainerCodec<R, K> withElement(Codec<R> newElementCodec) {
			return (ContainerCodec<R, K>) new ArrayCodec<>(newElementCodec, constructor);
		}
	}

	public static final class EnumCodec<E> extends Codec<E> implements NullAware {
		private final ImmutableBiMap<String, E> constants;
		private final boolean supportsNull;
		private EnumCodec<E> nullableCounterpart;
		private Class<E> type;

		public EnumCodec(Class<E> type, Function<E, String> naming, boolean supportsNull) {
			this(type, indexConstants(type, naming), supportsNull);
		}

		EnumCodec(Class<E> type, ImmutableBiMap<String, E> constants, boolean supportsNull) {
			this.type = type;
			this.constants = constants;
			this.supportsNull = supportsNull;
		}

		private static <E> ImmutableBiMap<String, E> indexConstants(Class<E> type, Function<E, String> naming) {
			ImmutableBiMap.Builder<String, E> builder = ImmutableBiMap.builder();
			for (E e : type.getEnumConstants()) {
				builder.put(naming.apply(e), e);
			}
			return builder.build();
		}

		@Override
		public boolean supportsNull() {
			return supportsNull;
		}

		@Override
		public Codec<E> toNullable() {
			if (supportsNull) return this;
			return nullableCounterpart == null
					? nullableCounterpart = new EnumCodec<>(type, constants, true)
					: nullableCounterpart;
		}

		@Override
		public E decode(In in) throws IOException {
			CharSequence name = in.takeString();
			E e = constants.get(name);
			if (e == null) in.unexpected(
					"Cannot read " + type + ". Was " + name + " while supported only " + constants.keySet());
			return e;
		}

		@Override
		public void encode(Out out, E instance) throws IOException {
			String name = constants.inverse().get(instance);
			if (name == null) out.unexpected("Wrong instance of " + type + ": " + instance);
			out.putString(name);
		}
	}

	private static Codec.Factory SCALARS = new Codec.Factory() {
		private final ScalarCodec<Integer> forInt = new ScalarCodec<>(ScalarCodec.INT, false);
		private final ScalarCodec<Long> forLong = new ScalarCodec<>(ScalarCodec.LONG, false);
		private final ScalarCodec<Double> forDouble = new ScalarCodec<>(ScalarCodec.DOUBLE, false);
		private final ScalarCodec<Boolean> forBoolean = new ScalarCodec<>(ScalarCodec.BOOLEAN, false);
		private final ScalarCodec<String> forString = new ScalarCodec<>(ScalarCodec.STRING, false);

		private final ImmutableMap<Class<?>, Codec<?>> codecs = ImmutableMap.<Class<?>, Codec<?>>builder() // @formatter:off
						.put(int.class, forInt).put(Integer.class, forInt)
						.put(long.class, forLong).put(Long.class, forLong)
						.put(double.class, forDouble).put(Double.class, forDouble)
						.put(boolean.class, forBoolean).put(Boolean.class, forBoolean)
						.put(String.class, forString)
						.build(); // @formatter:on

		@SuppressWarnings("unchecked")
		@Override
		public @Nullable <T> Codec<T> get(Resolver lookup, TypeToken<T> type) {
			return (Codec<T>) codecs.get(type.getRawType());
		}

		@Override
		public String toString() {
			return "Codec.Factory for int, long, double, boolean, String";
		}
	};

	// Albeit a lot of codecs for structures/objects will decide to encode decode
	// scalars by themselves, there's built-in codec for scalar (primitives + wrappers + string)
	// to implement reflective codecs.
	@SuppressWarnings("unchecked")
	static final class ScalarCodec<T> extends Codec<T> implements NullAware {
		static final int INT = 0;
		static final int LONG = 1;
		static final int DOUBLE = 2;
		static final int BOOLEAN = 3;
		static final int STRING = 4;

		private final boolean supportsNull;
		private final int type;
		private ScalarCodec<T> nullableCounterpart;

		ScalarCodec(int type, boolean supportsNull) {
			this.type = type;
			this.supportsNull = supportsNull;
		}

		@Override
		public boolean supportsNull() {
			return supportsNull;
		}

		@Override
		public Codec<T> toNullable() {
			if (supportsNull) return this;
			return nullableCounterpart == null
					? nullableCounterpart = new ScalarCodec<>(type, true)
					: nullableCounterpart;
		}

		// this call forces autoboxing of argument
		private T box(Object value) {
			return (T) value;
		}

		@Override
		public T decode(In in) throws IOException {
			if (supportsNull && in.peek() == At.NULL) {
				in.skip();
			}
			switch (type) { // @formatter:off
			case INT: return box(in.takeInt());
			case LONG: return box(in.takeLong());
			case DOUBLE: return box(in.takeDouble());
			case BOOLEAN: return box(in.takeBoolean());
			case STRING: return box(in.takeString());
			default: throw Unreachable.exhaustive();
			} // @formatter:on
		}

		@Override
		public void encode(Out out, T instance) throws IOException {
			if (instance == null) {
				if (!supportsNull) {
					out.unexpected("codec doesn't support null values");
				}
				out.putNull();
				return;
			}
			switch (type) { // @formatter:off
			case INT: out.putInt(((Number) instance).intValue()); break;
			case LONG: out.putLong(((Number) instance).longValue()); break;
			case DOUBLE: out.putDouble(((Number) instance).doubleValue()); break;
			case BOOLEAN: out.putBoolean(((Boolean) instance).booleanValue()); break;
			case STRING: out.putString(instance.toString()); break;
			} // @formatter:on
		}
	}

	private static Codec.Factory OPTIONALS = new Codec.Factory() {
		private final Type optionalTypeParameter = Optional.class.getTypeParameters()[0];
		@SuppressWarnings("unchecked") // runtime token + checks
		@Override
		public @Nullable <T> Codec<T> get(Resolver lookup, TypeToken<T> type) {
			Class<?> rawType = type.getRawType();
			if (rawType == Optional.class) {
				Codec<?> codec = lookup.get(type.resolveType(optionalTypeParameter));
				return (Codec<T>) new OptionalCodec<>((Codec<Object>) codec);
			}
			if (rawType == OptionalInt.class) {
				return (Codec<T>) new OptionalPrimitive(OptionalPrimitive.Variant.INT);
			}
			if (rawType == OptionalLong.class) {
				return (Codec<T>) new OptionalPrimitive(OptionalPrimitive.Variant.LONG);
			}
			if (rawType == OptionalDouble.class) {
				return (Codec<T>) new OptionalPrimitive(OptionalPrimitive.Variant.DOUBLE);
			}
			return null;
		}

		@Override
		public String toString() {
			return "Codec.Factory for Optional<E>, Optional{Int|Long|Double}";
		}
	};

	public static class OptionalPrimitive extends Codec<Object> implements NullAware {
		enum Variant {
			INT, LONG, DOUBLE
		}
		private final Variant type;
		public OptionalPrimitive(Variant type) {
			this.type = type;
		}
		@Override
		public Object decode(In in) throws IOException {
			if (in.peek() == At.NULL) {
				in.takeNull();
				switch (type) { // @formatter:off
				case INT: return OptionalInt.empty();
				case LONG: return OptionalLong.empty();
				case DOUBLE: return OptionalDouble.empty();
				default: throw Unreachable.exhaustive();
				}
			}
			switch (type) { // @formatter:off
			case INT: return OptionalInt.of(in.takeInt());
			case LONG: return OptionalLong.of(in.takeLong());
			case DOUBLE: return OptionalDouble.of(in.takeDouble());
			default: throw Unreachable.exhaustive();
			} // @formatter:on
		}
		@Override
		public void encode(Out out, Object instance) throws IOException {
			if (instance != null) {
				switch (type) {
				case INT:
					OptionalInt i = ((OptionalInt) instance);
					if (i.isPresent()) {
						out.putInt(i.getAsInt());
						return;
					}
					break;
				case LONG:
					OptionalLong l = ((OptionalLong) instance);
					if (l.isPresent()) {
						out.putLong(l.getAsLong());
						return;
					}
					break;
				case DOUBLE:
					OptionalDouble d = ((OptionalDouble) instance);
					if (d.isPresent()) {
						out.putDouble(d.getAsDouble());
						return;
					}
					break;
				}
			}
			// every present case should return above
			out.putNull();
		}

		@Override
		public boolean supportsNull() {
			return true;
		}
		@Override
		public String toString() {
			switch (type) { // @formatter:off
			case INT: return "Codec<OptionalInt>";
			case LONG: return "Codec<OptionalLong>";
			case DOUBLE: return "Codec<OptionalDouble>";
			default: throw Unreachable.exhaustive();
			} // @formatter:on
		}
	}

	public static class OptionalCodec<E> extends ContainerCodec<E, Optional<E>> implements NullAware {
		private final Codec<E> elementCodec;
		public OptionalCodec(Codec<E> elementCodec) {
			this.elementCodec = elementCodec;
		}
		@Override
		public Optional<E> decode(In in) throws IOException {
			if (in.peek() == At.NULL) {
				in.takeNull();
				return Optional.empty();
			}
			return Optional.ofNullable(elementCodec.decode(in)); // Optional.of ?
		}
		@Override
		public boolean supportsNull() {
			return true;
		}
		@Override
		public void encode(Out out, Optional<E> instance) throws IOException {
			if (instance != null && instance.isPresent()) {
				elementCodec.encode(out, instance.get());
			} else {
				out.putNull();
			}
		}
		@Override
		public String toString() {
			return "Codec<Optional<E>> for " + elementCodec;
		}
		@Override
		public Codec<E> element() {
			return elementCodec;
		}
		@SuppressWarnings("unchecked")
		@Override
		public <R, K> ContainerCodec<R, K> withElement(Codec<R> newElement) {
			return (ContainerCodec<R, K>) new OptionalCodec<>(newElement);
		}
	}

	private static class StringValueIo implements In, Out {
		private @Nullable String value;

		private void onlyStringrExpected() throws IOException {
			unexpected("Only takeString() expected");
		}

		@Override
		public String getPath() {
			return "$0";
		}

		@Override
		public void expect(boolean condition, Supplier<String> message) throws IOException {
			if (!condition) throw new IOException(message.get());
		}

		@Override
		public Object adapts() {
			return value;
		}

		@Override
		public int takeInt() throws IOException {
			return Integer.parseInt(value);
		}

		@Override
		public long takeLong() throws IOException {
			return Long.parseLong(value);
		}

		@Override
		public double takeDouble() throws IOException {
			return Long.parseLong(value);
		}

		@Override
		public boolean takeBoolean() throws IOException {
			return Boolean.parseBoolean(value);
		}

		@Override
		public void takeNull() throws IOException {
			if (value != null) onlyStringrExpected();
		}

		@Override
		public void skip() throws IOException {
			value = null;
		}

		@Override
		public CharSequence takeString() throws IOException {
			expectStringValue();
			return value;
		}

		private void expectStringValue() throws IOException {
			if (value == null) unexpected("no string available");
		}

		@Override
		public Object takeSpecial() throws IOException {
			return value;
		}

		@Override
		public boolean hasNext() throws IOException {
			onlyStringrExpected();
			return false;
		}

		@Override
		public void beginArray() throws IOException {
			onlyStringrExpected();
		}

		@Override
		public void endArray() throws IOException {
			onlyStringrExpected();
		}

		@Override
		public @Field int takeField() throws IOException {
			onlyStringrExpected();
			throw Unreachable.contractual();
		}

		@Override
		public void endStruct() throws IOException {
			onlyStringrExpected();
		}

		@Override
		public void putInt(int i) throws IOException {
			value = String.valueOf(i);
		}

		@Override
		public void putLong(long l) throws IOException {
			value = String.valueOf(l);
		}

		@Override
		public void putDouble(double d) throws IOException {
			value = String.valueOf(d);
		}

		@Override
		public void putBoolean(boolean b) throws IOException {
			value = String.valueOf(b);
		}

		@Override
		public void putSpecial(Object o) throws IOException {
			value = o.toString();
		}

		@Override
		public void putNull() throws IOException {
			value = null;
		}

		@Override
		public void putString(CharSequence s) throws IOException {
			value = s.toString();
		}

		@Override
		public void putField(@Field int field) throws IOException {
			onlyStringrExpected();
		}

		@Override
		public At peek() throws IOException {
			return value == null ? At.NULL : At.STRING;
		}

		@Override
		public void beginStruct(FieldIndex f) throws IOException {
			onlyStringrExpected();
		}
	}

	public abstract class ForwardingOut implements Out {
		protected abstract Out delegate();

		@Override
		public void putInt(int i) throws IOException {
			delegate().putInt(i);
		}

		@Override
		public void putLong(long l) throws IOException {
			delegate().putLong(l);
		}

		@Override
		public void putDouble(double d) throws IOException {
			delegate().putDouble(d);
		}

		@Override
		public void putBoolean(boolean b) throws IOException {
			delegate().putBoolean(b);
		}

		@Override
		public void putSpecial(Object o) throws IOException {
			delegate().putSpecial(o);
		}

		@Override
		public void putNull() throws IOException {
			delegate().putNull();
		}

		@Override
		public void putString(CharSequence s) throws IOException {
			delegate().putString(s);
		}

		@Override
		public void endArray() throws IOException {
			delegate().endArray();
		}

		@Override
		public void beginArray() throws IOException {
			delegate().beginArray();
		}

		@Override
		public void beginStruct(FieldIndex f) throws IOException {
			delegate().beginStruct(f);
		}

		@Override
		public void putField(@Field int field) throws IOException {
			delegate().putField(field);
		}

		@Override
		public void endStruct() throws IOException {
			delegate().endStruct();
		}

		@Override
		public String getPath() {
			return delegate().getPath();
		}

		@Override
		public void expect(boolean condition, Supplier<String> message) throws IOException {
			delegate().expect(condition, message);
		}

		@Override
		public void unexpected(String message) throws IOException {
			delegate().unexpected(message);
		}

		@Override
		public Object adapts() {
			return delegate().adapts();
		}
	}

	public abstract class ForwardingIn implements In {
		protected abstract Out delegate();

		public void putInt(int i) throws IOException {
			delegate().putInt(i);
		}

		public void putLong(long l) throws IOException {
			delegate().putLong(l);
		}

		public void putDouble(double d) throws IOException {
			delegate().putDouble(d);
		}

		public void putBoolean(boolean b) throws IOException {
			delegate().putBoolean(b);
		}

		public void putSpecial(Object o) throws IOException {
			delegate().putSpecial(o);
		}

		public void putNull() throws IOException {
			delegate().putNull();
		}

		public void putString(CharSequence s) throws IOException {
			delegate().putString(s);
		}

		@Override
		public void endArray() throws IOException {
			delegate().endArray();
		}

		@Override
		public void beginArray() throws IOException {
			delegate().beginArray();
		}

		@Override
		public void beginStruct(FieldIndex f) throws IOException {
			delegate().beginStruct(f);
		}

		public void putField(@Field int field) throws IOException {
			delegate().putField(field);
		}

		@Override
		public void endStruct() throws IOException {
			delegate().endStruct();
		}

		@Override
		public String getPath() {
			return delegate().getPath();
		}

		@Override
		public void expect(boolean condition, Supplier<String> message) throws IOException {
			delegate().expect(condition, message);
		}

		@Override
		public void unexpected(String message) throws IOException {
			delegate().unexpected(message);
		}

		@Override
		public Object adapts() {
			return delegate().adapts();
		}
	}
}