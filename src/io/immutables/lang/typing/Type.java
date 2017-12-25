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

	interface Arrow {
		Type in();
		Type out();
	}

	interface Converted extends Type {
		Arrow arrow();
		default Type to() {
			return arrow().out();
		}

		static Converted by(Arrow arrow) {
			return Impl.converted(arrow);
		}
	}

	interface Feature extends Arrow, Named, Parameterized {

		static Feature simple(Symbol name, Type in, Type out) {
			return Impl.feature(name, in, out);
		}

		static Feature missing(Symbol name) {
			return Impl.missing(name);
		}

		// temporary, to be rewamped into concepts, as anchor/reminder
		Symbol to = Symbol.from("to");

		default boolean isDefined() {
			return out() != Undefined;
		}
	}

	interface Constructor extends Arrow, Named {
		@Override
		Declared out();

		static Constructor unnamed(Declared out, Type in) {
			return Impl.constructor(out, in);
		}
	}

	interface Declared extends Type, Named {
		Type[] arguments();
		Type withArguments(Type[] arguments);
		Constructor[] constructors();

		static Declared simple(Symbol name, Type... arguments) {
			return Impl.declared(name, arguments, Type.Undefined);
		}

		static Declared constructed(Symbol name, Type in, Type... arguments) {
			return Impl.declared(name, arguments, in);
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

		default O converted(Converted c, I in) {
			return otherwise(c, in);
		}

		default O otherwise(Type t, I in) {
			throw new UnsupportedOperationException("cannot handle type " + t + " with input: " + in);
		}
	}
}
