package io.immutables.collect;

import java.util.function.BiFunction;

public interface Foldable<E> {

	<A> A fold(A left, BiFunction<A, E, A> reducer);

	<A> A fold(BiFunction<E, A, A> reducer, A right);

	E reduce(BiFunction<E, E, E> reducer);
}