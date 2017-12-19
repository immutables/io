package io.immutables.lang.type;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.Arrays;
import java.util.function.Function;

// Rubbish
@Deprecated
public final class Substitute<T extends Type> implements Function<T, Type> {
	private final Type[] types;
	private final int size;

	@SuppressWarnings("unchecked") // safe unchecked, empty immutable.
	public static <T extends Type> Substitute<T> init() {
		return (Substitute<T>) INIT;
	}

	private Substitute(Type[] types, int size) {
		this.types = types;
		this.size = size;
	}

	@Override
	public Type apply(T type) {
		for (int i = 0; i < size; i += INDEX_INCREMENT) {
			if (types[i].eq(type)) {
				return types[i + 1];
			}
		}
		return type;
	}

	public boolean has(T type) {
		return type != Type.Undefined && !apply(type).eq(type);
	}

	public int size() {
		return size / INDEX_INCREMENT;
	}

	public Substitute<T> with(T from, Type to) {
		Type[] types = Arrays.copyOf(this.types, size + INDEX_INCREMENT);
		types[size] = from;
		types[size + 1] = to;
		return new Substitute<>(types, size + INDEX_INCREMENT);
	}

	@Override
	public String toString() {
		ToStringHelper helper = MoreObjects.toStringHelper(this);
		for (int i = 0; i < size; i += INDEX_INCREMENT) {
			helper.addValue(types[i] + " => " + types[i + 1]);
		}
		return helper.toString();
	}

	private static final int INDEX_INCREMENT = 2;
	private static final Substitute<Type> INIT = new Substitute<>(new Type[0], 0);
}
