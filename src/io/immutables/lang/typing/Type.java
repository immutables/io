package io.immutables.lang.typing;

import io.immutables.grammar.Symbol;

public interface Type {
	default <I, O> O accept(Visitor<I, O> v, I in) {
		return v.otherwise(this, in);
	}

	default Feature getFeature(Symbol name) {
		return Impl.getFeature(features(), name);
	}

	default Feature[] features() {
		return Impl.noFeatures();
	}

	interface Named {
		Symbol name();
	}

	interface Parameterized {
		Parameter[] parameters();
	}

	interface Feature extends Named, Parameterized {
		Type in();
		Type out();

		static Feature simple(Symbol name, Type in, Type out) {
			return Impl.feature(name, in, out);
		}

		static Feature missing(Symbol name) {
			return Impl.missing(name);
		}
	}

	interface Nominal extends Type, Named {
		Type[] arguments();

		static Nominal simple(Symbol name, Type... arguments) {
			return Impl.nominal(name, arguments);
		}
	}

	interface Unresolved extends Type, Named {
		static Unresolved by(Symbol name) {
			return Impl.unresolved(name);
		}
	}

	interface Variable extends Type, Named {
		static Variable allocate(Symbol name) {
			return Impl.allocate(name);
		}
	}

	interface Parameter extends Type, Named {
		int index();

		static Parameter declare(int index, Symbol name) {
			return Impl.declare(index, name);
		}
	}

	Type Undefined = Impl.Undefined;
	Product Empty = Impl.Empty;

	interface Structural extends Type {}

	interface Product extends Structural {
		Type[] components();

		static Product of(Type... components) {
			return Impl.product(components);
		}
	}

	interface Visitor<I, O> {
		default O variable(Variable v, I in) {
			return otherwise(v, in);
		}

		default O parameter(Parameter p, I in) {
			return otherwise(p, in);
		}

		default O nominal(Nominal d, I in) {
			return otherwise(d, in);
		}

		default O product(Product p, I in) {
			return otherwise(p, in);
		}

		default O empty(I in) {
			return otherwise(Type.Empty, in);
		}

		default O unresolved(I in, Unresolved f) {
			return otherwise(f, in);
		}

		default O undefined(I in) {
			return otherwise(Undefined, in);
		}

		default O otherwise(Type t, I in) {
			throw new UnsupportedOperationException("cannot handle type " + t + " with input: " + in);
		}
	}
}
