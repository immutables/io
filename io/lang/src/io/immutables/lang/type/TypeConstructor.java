package io.immutables.lang.type;

import io.immutables.collect.Vect;

interface TypeConstructor extends Named, Parameterizable {
	Vect<Type.Parameter> parameters();
	Type.Nominal instantiate(Vect<Type> arguments);

	default Type.Nominal instantiate(Type... arguments) {
		return instantiate(Vect.of(arguments));
	}
}
