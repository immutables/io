package io.immutables.lang.type;

import io.immutables.collect.Vect;
import org.immutables.value.Value;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;

@Enclosing
public interface Type {
	interface Visitor<R> {
		default R parameter(Parameter p) {
			return any(p);
		}

		default R variable(Variable v) {
			return any(v);
		}

		default R declared(Declared d) {
			return any(d);
		}

		default R empty(Empty v) {
			return any(v);
		}

		default R product(Product p) {
			return any(p);
		}

		default R record(Record r) {
			return any(r);
		}

		default R variant(Variant v) {
			return any(v);
		}

		default R sequence(Sequence s) {
			return any(s);
		}

		default R function(Function f) {
			return any(f);
		}

		default R any(Type t) {
			throw new UnsupportedOperationException("cannot handle type: " + t);
		}
	}

	Vect<Feature> features();

	default <R> R accept(Visitor<R> visitor) {
		return visitor.any(this);
	}

	default boolean eq(Type type) {
		return equals(type);
	}

	interface Parameterized {
		Vect<Parameter> parameters();
		Vect<Constraint> constraints();
	}

	@Immutable
	interface Concept extends Parameterized, Named {
		Vect<Feature> features();
		class Builder extends ImmutableType.Concept.Builder {}
	}

	@Immutable
	interface Feature extends Arrow, Parameterized, Named {
		class Builder extends ImmutableType.Feature.Builder {}
	}

	interface Constructor extends Arrow, Named {}

	/**
	 * Arrow is any program element that is being typechecked as
	 * type tranforming arrow. This can be methods,
	 * operators as well as extraction of elements from collection in for comprehension etc.
	 */
	interface Arrow {
		Type in();
		Type out();
	}

	interface Named {
		Name name();
	}

	/**
	 * Parameters are used in the context of type/method declaration and constraints.
	 * Parameter have no methods/associated, however, projected {@link Variable}s
	 * which reference it will have them given there's some knowledge
	 * of the structure and conformance of the types were given in contraint
	 */
	interface Parameter extends Type, Named {
		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.parameter(this);
		}

		static Parameter allocate(Name name) {
			return new Parameter() {
				@Override
				public Name name() {
					return name;
				}
				@Override
				public Vect<Feature> features() {
					return Vect.of();
				}
				@Override
				public String toString() {
					return "<" + name + ">";
				}
			};
		}
	}

	interface Variable extends Type, Named {
		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.variable(this);
		}
	}

	interface Structural extends Type {}

	interface Unresolved extends Type {}

	interface Declared extends Type, Parameterized, Named {
		Vect<Type> arguments();

		Vect<Constructor> constructors();

		/** @param arguments */
		default Declared apply(Vect<Type> arguments) {
			throw new UnsupportedOperationException();
		}

		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.declared(this);
		}
	}

	@Immutable(copy = false, singleton = true, builder = false)
	interface Empty extends Structural {
		static Empty of() {
			return ImmutableType.Empty.of();
		}

		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.empty(this);
		}
	}

	@Immutable
	interface Variant extends Structural {
		@Value.Parameter
		Vect<Type> alternatives();

		class Builder extends ImmutableType.Variant.Builder {}

		static Variant of(Type... alternatives) {
			return ImmutableType.Variant.of(Vect.of(alternatives));
		}

		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.variant(this);
		}
	}

	@Immutable
	interface Product extends Structural {
		@Value.Parameter
		Vect<Type> components();

		class Builder extends ImmutableType.Product.Builder {}

		static Product of(Type... components) {
			return ImmutableType.Product.of(Vect.of(components));
		}

		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.product(this);
		}
	}

	@Immutable
	interface Record extends Structural {
		// it is unclear now how record structural type can be thought of:
		// as a bag of attributes/features or full fledged interface-like things with a
		// operations/methods
		// later: isn't this is the same
		Vect<Feature> attributes();

		class Builder extends ImmutableType.Record.Builder {}

		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.record(this);
		}
	}

	@Immutable
	interface Function extends Structural, Arrow {
		class Builder extends ImmutableType.Function.Builder {}

		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.function(this);
		}
	}

	@Immutable
	interface Sequence extends Structural {
		// Sequence types are not thought out yet
		@Override
		public default <R> R accept(Visitor<R> visitor) {
			return visitor.sequence(this);
		}
	}

	@Immutable
	abstract class DeclaredImpl implements Declared {
		static class Builder extends ImmutableType.DeclaredImpl.Builder {}
	}

	Type Undefined = new Type() {
		@Override
		public Vect<Feature> features() {
			return Vect.of();
		}

		@Override
		public String toString() {
			return "Type.Undefined";
		}

		public <R> R accept(Visitor<R> visitor) {
			throw new UnsupportedOperationException("Cannot visit " + this);
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return false;
		}

		public boolean eq(Type type) {
			return false;
		}
	};
}
