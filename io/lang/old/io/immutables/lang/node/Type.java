package io.immutables.lang.node;

import io.immutables.collect.Vect;

public interface Type {

	default <I, O> O accept(Visitor<I, O> v, I in) {
		return v.otherwise(this, in);
	}

	default Feature getFeature(Name name) {
		return Impl.getFeature(features(), name);
	}

	default Vect<Feature> features() {
		return Vect.of();
	}

	default boolean eq(Type type) {
		return equals(type);
	}

	interface Named {
		Name name();
	}

	interface Parameterized {
		Parameter[] parameters();
	}

	interface Arrow {
		Type in();
		Type out();
	}

	interface Feature extends Arrow, Named, Parameterized {

		static Feature simple(Name name, Type in, Type out) {
			return Impl.feature(name, in, out);
		}

		static Feature missing(Name name) {
			return Impl.missing(name);
		}

		default boolean isDefined() {
			return out() != Undefined;
		}
	}

	interface Declared extends Type, Named, Parameterized {

		static Declared simple(Name name, Type... arguments) {
			return Impl.declared(name, arguments, Type.Undefined, Vect.of());
		}

		default boolean sameDeclaration(Type.Declared actual) {
			return name().equals(actual.name());
		}
	}

	interface Unresolved extends Type, Named {
		static Unresolved by(Name name) {
			return Impl.unresolved(name);
		}
	}

	interface Variable extends Type, Named {
		static Variable allocate(Name name) {
			return Impl.allocate(name);
		}
	}

	interface Parameter extends Type, Named {
		int index();

		static Parameter declare(int index, Name name) {
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

		default O declared(Declared d, I in) {
			return otherwise(d, in);
		}

		default O product(Product p, I in) {
			return otherwise(p, in);
		}

		default O empty(I in) {
			return otherwise(Type.Empty, in);
		}

		default O undefined(I in) {
			return otherwise(Undefined, in);
		}

		default O unresolved(Unresolved f, I in) {
			return otherwise(f, in);
		}

		default O otherwise(Type t, I in) {
			throw new UnsupportedOperationException("cannot handle type " + t + " with input: " + in);
		}
	}
}
