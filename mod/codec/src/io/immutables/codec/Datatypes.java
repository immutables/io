package io.immutables.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import io.immutables.codec.Datatype.Builder;
import io.immutables.codec.Datatype.Getter;
import io.immutables.codec.Datatype.Setter;
import io.immutables.codec.Datatype.Violation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class Datatypes {
	private Datatypes() {}
	
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
		for (Field f : raw.getFields()) {
			builder.add(new FieldFeature<>(f, type.resolveType(f.getGenericType())));
		}
		ImmutableList<?> features = builder.build();

		return new Datatype<T>() {
			@Override
			public List<Getter<T, ?>> getters() {
				return (List<Getter<T, ?>>) features;
			}

			@Override
			public List<Setter<T, ?>> setters() {
				return (List<Setter<T, ?>>) features;
			}

			@Override
			public Builder<T> builder() {
				return new InstanceBuilder<>((Class<T>) raw);
			}

			@Override
			public String toString() {
				return "Datatype.forStruct(" + type + ")";
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
		public List<Violation> verify() {
			// this struct builder do not do any validation
			return Collections.emptyList();
		}
	}

	@SuppressWarnings("unchecked") // runtime token + checks
	private static class FieldFeature<T, F> implements Getter<T, F>, Setter<T, F> {
		private final Field field;
		private final TypeToken<F> type;

		FieldFeature(Field field, TypeToken<F> type) {
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

		@Override
		public F get(T instance) {
			try {
				return (F) field.get(instance);
			} catch (IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void set(Builder<T> builder, F value) {
			try {
				field.set(((InstanceBuilder<T>) builder).instance, value);
			} catch (IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public boolean optional() {
			return true;
		}

		@Override
		public String toString() {
			return field.getName() + ": " + type;
		}
	}
}
