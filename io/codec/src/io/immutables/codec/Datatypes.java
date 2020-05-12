package io.immutables.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import io.immutables.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import org.immutables.data.Datatype;
import org.immutables.data.Datatype.Builder;
import org.immutables.data.Datatype.Feature;
import org.immutables.data.Datatype.Violation;
import org.immutables.value.Generated;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;

public class Datatypes {
	private Datatypes() {}

	public static <T> Datatype<T> construct(TypeToken<T> type) {
		@Nullable Datatype<T> datatype = lookupDatatype(type);
		if (datatype != null) return datatype;
		return forStruct(type);
	}

	@SuppressWarnings("unchecked") // based on type token runtime check and convention
	private static @Nullable <T> Datatype<T> lookupDatatype(TypeToken<T> type) {
		// these transitions are based on current datatype generation conventions
		Class<?> definitionClass = getDefinitionClass(type.getRawType());
		// don't torture classes which are not probable to be datatype definition
		if (!Modifier.isAbstract(definitionClass.getModifiers())) return null;
		
		Class<?> topLevelDefiner = getTopLevel(definitionClass);
		@Nullable Class<?> datatypeConstructor = loadFromTheSamePackage(
				topLevelDefiner, DATATYPES_PREFIX + topLevelDefiner.getSimpleName());

		if (datatypeConstructor != null) {
			try {
				return (Datatype<T>) datatypeConstructor
						.getMethod(CONSTRUCT_METHOD, TypeToken.class)
						.invoke(null, type);
			} catch (InvocationTargetException ex) {
				throwIfUnchecked(ex.getCause());
				throw new RuntimeException(ex.getCause());
			} catch (ReflectiveOperationException | SecurityException ex) {
				throw new RuntimeException(ex.getCause());
			}
		}
		return null;
	}

	private static Class<?> getDefinitionClass(Class<?> c) {
		@Nullable Generated generated = c.getAnnotation(Generated.class);
		if (generated != null) {
			// also transform from canonical to binary name
			String className = generated.from().replace('.', '$');
			Class<?> abstractType = loadFromTheSamePackage(c, className);
			if (abstractType != null) return abstractType;
		}
		return c;
	}

	private static @Nullable Class<?> loadFromTheSamePackage(Class<?> c, String name) {
		String prefix = c.getPackage().getName();
		if (!prefix.isEmpty()) prefix += ".";
		try {
			return Class.forName(prefix + name, false, c.getClassLoader());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	private static Class<?> getTopLevel(Class<?> c) {
		for (;;) {
			Class<?> enclosing = c.getEnclosingClass();
			if (enclosing == null) break;
			c = enclosing;
		}
		return c;
	}

	@SuppressWarnings("unchecked") // covariant immutable cast + runtime checks
	public static <T> Datatype<T> forStruct(Class<T> type) {
		checkArgument(type.getTypeParameters().length == 0, "must not have type parameters,"
				+ " use forStruct(TypeToken) instead with valid type arguments. %s", type);
		return forStruct(TypeToken.of(type));
	}

	@SuppressWarnings("unchecked") // covariant immutable cast + runtime checks
	public static <T> Datatype<T> forStruct(TypeToken<T> type) {
		Class<?> raw = type.getRawType();
		checkArgument(!raw.isInterface() && !raw.isEnum() && !Modifier.isAbstract(raw.getModifiers()),
				"must be non-abstract class: but was %s", type);
		requireNonNull(raw.getCanonicalName(), "must have canonical name");

		ImmutableList.Builder<FieldFeature<T, ?>> builder = ImmutableList.builder();
		int index = 0;
		for (Field f : raw.getFields()) {
			builder.add(new FieldFeature<>(index++, f, type.resolveType(f.getGenericType())));
		}
		ImmutableList<FieldFeature<T, ?>> features = builder.build();

		return new Datatype<T>() {
			@Override
			public Builder<T> builder() {
				return new InstanceBuilder<>((Class<T>) raw);
			}

			@Override
			public String toString() {
				return "Datatype.forStruct(" + type + ")";
			}

			@Override
			public String name() {
				return raw.getSimpleName();
			}

			@Override
			public TypeToken<T> type() {
				return type;
			}

			@SuppressWarnings("unchecked") // covariant immutable list cast
			@Override
			public List<Feature<T, ?>> features() {
				return (List<Feature<T, ?>>) (Object) features;
			}

			@Override
			public <F> F get(Feature<T, F> feature, T instance) {
				return ((FieldFeature<T, F>) feature).get(instance);
			}
		};
	}

	private static class InstanceBuilder<T> implements Builder<T> {
		private T instance;

		InstanceBuilder(Class<T> type) {
			try {
				instance = type.newInstance();
			} catch (InstantiationException | IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public T build() {
			return instance;
		}

		@Override
		public <F> void set(Feature<T, F> feature, F value) {
			((FieldFeature<T, F>) feature).set(this, value);
		}

		@Override
		public List<Violation> verify() {
			// this struct builder do not do any validation
			return Collections.emptyList();
		}
	}

	@SuppressWarnings("unchecked") // runtime token + checks
	private static class FieldFeature<T, F> implements Feature<T, F> {
		private final Field field;
		private final TypeToken<F> type;
		private final int index;

		FieldFeature(int index, Field field, TypeToken<F> type) {
			this.index = index;
			this.field = field;
			this.type = type;
		}

		@Override
		public String name() {
			return field.getName();
		}

		@Override
		public TypeToken<F> type() {
			return type;
		}

		F get(T instance) {
			try {
				return (F) field.get(instance);
			} catch (IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}

		void set(Builder<T> builder, F value) {
			try {
				field.set(((InstanceBuilder<T>) builder).instance, value);
			} catch (IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public int index() {
			return index;
		}

		@Override
		public boolean nullable() {
			return !type.isPrimitive();
		}

		@Override
		public boolean supportsInput() {
			return true;
		}

		@Override
		public boolean supportsOutput() {
			return true;
		}

		@Override
		public boolean omittableOnInput() {
			return true;
		}

		@Override
		public boolean ignorableOnOutput() {
			return false;
		}

		@Override
		public String toString() {
			return field.getName() + ": " + type;
		}
	}

	private static final String CONSTRUCT_METHOD = "constuct"; // typo in generated code, to be fixed
	private static final String DATATYPES_PREFIX = "Datatypes_";
}