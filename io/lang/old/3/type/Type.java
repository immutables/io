package io.immutables.lang.type;

import io.immutables.collect.Vect;

public interface Type {

	Undefined Undefined = Types.Undefined;

	interface Undefined extends Type {}

	interface Structural extends Type {}

	// Do we need special case basic as interface/visitor case or have it just as implementation
	interface Basic extends Type, Named, Tystructor {}

	interface Variable extends Type, Named {}

	interface Nominal extends Type, Named, Parameterizable {
		Tystructor tystructor();
	}

	interface Product extends Structural {
		Product Empty = Types.Empty;

		Vect<Type> components();
	}

	/**
	 * Tystructor - Type Constructor: constructs type instances by applying type arguments substituting type parameters
	 */
	interface Tystructor extends Named {
		Vect<Variable> parameters();

		Type construct(Vect<Type> arguments);
	}

	//---
	boolean equals(Object type);

	<I, O> O accept(Visitor<I, O> v, I in);
	//static transft
}
