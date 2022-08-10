package io.immutables.lang.type;

import io.immutables.collect.Vect;
import java.util.function.Function;

// functor-ish wrapper around smth having a type
public interface Typed {
	Typed map(Function<Type, Type> fn);

	Vect<Typed> mapVect(Function<Type, Vect<Type>> fn);

//	Typed

	interface Arrow {

	}
}
