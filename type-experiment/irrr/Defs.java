package io.immutables.lang.type.irrr;

import io.immutables.type.Constraint;
import io.immutables.type.Type;
import io.immutables.type.Type.Parameter;
import io.immutables.collect.Vect;

// Imports provide lookup for extenal symbols
// Local definitions?
interface Defs {

	interface Parametrization {
		Vect<Type.Parameter> parameters();
		Vect<Constraint> constraints();
	}

	interface Typedef {

	}

	interface Concept {

	}

	interface Impl {}
}
