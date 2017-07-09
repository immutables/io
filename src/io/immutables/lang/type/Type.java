package io.immutables.lang.type;

import io.immutables.collect.Vect;
import org.immutables.value.Value;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;

@Enclosing
public interface Type {
	default <I, O> O accept(Visitor<I, O> v, I in) {
		return v.any(in, this);
	}

	default Vect<Feature> features() {
		return Vect.of();
	}

	default boolean eq(Type t) {
		return equals(t);
	}

	interface Parameterized {
		Vect<Variable> parameters();
		Vect<Constraint> constraints();
	}

	@Immutable
	interface Concept extends Parameterized, Named {
		Vect<Feature> features();
		class Builder extends ImmutableType.Concept.Builder {}
	}

	interface Feature extends Arrow, Named {}

	/** This is not type constructor, just constructor */
	interface Constructor extends Arrow, Named {}

	/**
	 * Arrow is any program element that is being typechecked as
	 * type tranforming arrow. This can be methods,
	 * operators as well as extraction of elements from collection in for comprehension etc.
	 */
	interface Arrow extends Parameterized {
		Type in();
		Type out();

		Arrow with(Type in, Type out);
	}

	interface Named {
		Name name();
	}

	interface Variable extends Type, Named {
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.variable(in, this);
		}

		static Variable allocate(Name name) {
			return new Variable() {
				@Override
				public Name name() {
					return name;
				}
				@Override
				public String toString() {
					return "<" + name + ">";
				}
				@Override
				public boolean eq(Type t) {
					return this == t;
				}
			};
		}
	}

	interface Structural extends Type {}

	interface Unresolved extends Type, Named {
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.unresolved(in, this);
		}
		static Unresolved of(Name name) {
			return new Unresolved() {
				@Override
				public Name name() {
					return name;
				}
				@Override
				public String toString() {
					return "!" + name + ")";
				}
			};
		}
	}

	interface Declared extends Type, Parameterized, Named {
		Vect<Type> arguments();

		Vect<Constructor> constructors();

		/** @param arguments */
		Declared applyArguments(Vect<Type> arguments);

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.declared(in, this);
		}
	}

	// Not thought out well
	@Immutable
	interface Impl extends Parameterized {
		Vect<Feature> features();

		class Builder extends ImmutableType.Impl.Builder {}
	}

	@Immutable
	interface Variant extends Structural {
		@Value.Parameter
		Vect<Type> alternatives();

		class Builder extends ImmutableType.Variant.Builder {}

		static Variant of(Type... alternatives) {
			return of(Vect.of(alternatives));
		}

		static Variant of(Iterable<? extends Type> alternatives) {
			return ImmutableType.Variant.of(alternatives);
		}

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.variant(in, this);
		}
	}

	@Immutable(singleton = true)
	interface Product extends Structural {
		@Value.Parameter
		Vect<Type> components();

		class Builder extends ImmutableType.Product.Builder {}

		static Product of(Type... components) {
			return of(Vect.of(components));
		}

		static Product of(Iterable<? extends Type> components) {
			return ImmutableType.Product.of(components);
		}

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.product(in, this);
		}
	}

	@Immutable
	interface Record extends Structural {
		// it is unclear now how record structural type can be thought of:
		// as a bag of attributes/features or full fledged interface-like things with a
		// operations/methods
		// later: isn't this is the same
		@Override
		Vect<Feature> features();

		class Builder extends ImmutableType.Record.Builder {}

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.record(in, this);
		}
	}

	interface Function extends Structural, Arrow {
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.function(in, this);
		}
	}

	@Immutable
	interface Sequence extends Structural {
		// Sequence types are not thought out yet
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.sequence(in, this);
		}
	}

	Product Empty = Product.of();

	Type Undefined = new Type() {
		@Override
		public Vect<Feature> features() {
			return Vect.of();
		}

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			throw new UnsupportedOperationException("Cannot visit undefined type");
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return false;
		}

		@Override
		public boolean eq(Type type) {
			return false;
		}

		@Override
		public String toString() {
			return "???";
		}
	};

	interface Capture extends Type {
		Type in();

		static Capture of(Type in) {
			return new Capture() {
				@Override
				public Type in() {
					return in;
				}
				@Override
				public String toString() {
					return "$" + in;
				}
				@Override
				public boolean eq(Type t) {
					return this == t;
				}
			};
		}
	}

	@FunctionalInterface
	interface Resolver extends java.util.function.Function<Name, Type> {
		@Override
		Type apply(Name name);
	}

	interface Visitor<I, O> {
		default O variable(I in, Variable v) {
			return any(in, v);
		}

		default O capture(I in, Capture c) {
			return any(in, c);
		}

		default O declared(I in, Declared d) {
			return any(in, d);
		}

		default O product(I in, Product p) {
			return any(in, p);
		}

		default O record(I in, Record r) {
			return any(in, r);
		}

		default O variant(I in, Variant v) {
			return any(in, v);
		}

		default O sequence(I in, Sequence s) {
			return any(in, s);
		}

		default O function(I in, Function f) {
			return any(in, f);
		}

		default O unresolved(I in, Unresolved f) {
			return any(in, f);
		}

		default O any(I in, Type t) {
			throw new UnsupportedOperationException("cannot handle type: " + t + " and input " + in);
		}
	}

	interface Transformer<I> extends Visitor<I, Type> {

		@Override
		default Type any(I in, Type t) {
			return t;
		}

		@Override
		default Type record(I in, Record r) {
			// TODO
			throw new UnsupportedOperationException("unimplemented");
		}
	}
}
