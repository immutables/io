package io.immutables.lang.type;

import io.immutables.collect.Vect;

public interface Parameterizable {
	Vect<Type> arguments();
}
