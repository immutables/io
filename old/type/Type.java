package io.immutables.lang.type;

import io.immutables.collect.Vect;

public interface Type {

	interface Named {
		Name name();
	}

	interface Arity {
		Vect<Type> components();
	}

	interface Undefined extends Type {}

	interface Structural extends Type {}

	interface Empty extends Structural {}

	interface Product extends Structural, Arity {}

	interface Nominal extends Type, Named {}

	interface Parameter extends Nominal {
		int index();
	}

	interface Variable extends Nominal {}

	interface Declared extends Nominal {
		Vect<Type> arguments();
	}

	interface Unresolved extends Nominal {}

	interface Parameterized {
		Vect<Parameter> parameters();
	}

	interface Symbol extends Named, Parameterized {}

	interface Arrow extends Parameterized {
		Type in();
		Type out();
	}

	interface Feature extends Arrow, Named {}

	default <I, O> O accept(Visitor<I, O> v, I in) {
		return v.otherwise(this, in);
	}

	interface Visitor<I, O> {

		default O undefined(Undefined u, I in) {
			return otherwise(u, in);
		}

		default O variable(Variable v, I in) {
			return otherwise(v, in);
		}

		default O parameter(Parameter p, I in) {
			return otherwise(p, in);
		}

		default O declared(Declared d, I in) {
			return otherwise(d, in);
		}

		default O empty(Empty e, I in) {
			return otherwise(e, in);
		}

		default O product(Product p, I in) {
			return otherwise(p, in);
		}

		default O unresolved(Unresolved f, I in) {
			return otherwise(f, in);
		}

		default O otherwise(Type t, I in) {
			throw new UnsupportedOperationException("Cannot handle type " + t + " with input: " + in);
		}
	}
}
