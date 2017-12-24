package io.immutables.lang.type;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.Arrays;
import java.util.function.Function;

// Rubbish
@Deprecated
public final class Substitute<T extends Type22> implements Function<T, Type22> {
	private final Type22[] types;
	private final int size;

	@SuppressWarnings("unchecked") // safe unchecked, empty immutable.
	public static <T extends Type22> Substitute<T> init() {
		return (Substitute<T>) INIT;
	}

	private Substitute(Type22[] types, int size) {
		this.types = types;
		this.size = size;
	}

	@Override
	public Type22 apply(T type) {
		for (int i = 0; i < size; i += INDEX_INCREMENT) {
			if (types[i].eq(type)) {
				return types[i + 1];
			}
		}
		return type;
	}

	public boolean has(T type) {
		return type != Type22.Undefined && !apply(type).eq(type);
	}

	public int size() {
		return size / INDEX_INCREMENT;
	}

	public Substitute<T> with(T from, Type22 to) {
		Type22[] types = Arrays.copyOf(this.types, size + INDEX_INCREMENT);
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
	private static final Substitute<Type22> INIT = new Substitute<>(new Type22[0], 0);
}
