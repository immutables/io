package io.immutables.codec;

import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;
import java.util.List;
import java.util.NoSuchElementException;

public interface Datatype<T> {
	List<Getter<T, ?>> getters();
	List<Setter<T, ?>> setters();

	Builder<T> builder();

	interface Builder<T> {
		List<Violation> verify();
		T build();
	}

	interface Violation {
		Optional<Getter<?, ?>> setter();
		String rule();
		String message();
	}

	interface Feature<F> {
		String name();
		TypeToken<F> type();
	}

	interface Getter<T, F> extends Feature<F> {
		F get(T instance);
	}

	interface Setter<T, F> extends Feature<F> {
		void set(Builder<T> builder, F value);
		boolean optional();
	}

	@SuppressWarnings("unchecked") // runtime token check
	default <F> Getter<T, F> getter(String name, Class<F> type) {
		for (Getter<T, ?> g : getters()) {
			if (g.name().equals(name) && g.type().getType().equals(type)) {
				return (Getter<T, F>) g;
			}
		}
		throw new NoSuchElementException(name + ": " + type);
	}

	@SuppressWarnings("unchecked") // runtime token check
	default <F> Getter<T, F> getter(String name, TypeToken<F> type) {
		for (Getter<T, ?> g : getters()) {
			if (g.name().equals(name) && g.type().equals(type)) {
				return (Getter<T, F>) g;
			}
		}
		throw new NoSuchElementException(name + ": " + type);
	}

	@SuppressWarnings("unchecked") // runtime token check
	default <F> Setter<T, F> setter(String name, TypeToken<F> type) {
		for (Setter<T, ?> s : setters()) {
			if (s.name().equals(name) && s.type().equals(type)) {
				return (Setter<T, F>) s;
			}
		}
		throw new NoSuchElementException(name + ": " + type);
	}

	@SuppressWarnings("unchecked") // runtime token check
	default <F> Setter<T, F> setter(String name, Class<F> type) {
		for (Setter<T, ?> s : setters()) {
			if (s.name().equals(name) && s.type().getType().equals(type)) {
				return (Setter<T, F>) s;
			}
		}
		throw new NoSuchElementException(name + ": " + type);
	}
}
