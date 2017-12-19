package io.immutables.lang.type;

import io.immutables.collect.Vect;
import org.immutables.value.Value;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import static com.google.common.base.Preconditions.checkState;

@Enclosing
public interface Type {
	default <I, O> O accept(Visitor<I, O> v, I in) {
		return v.otherwise(this, in);
	}

	default boolean eq(Type t) {
		return equals(t);
	}

	default Vect<Feature> features() {
		return Vect.of();
	}

	default Feature getFeature(Name name) {
		return features()
				.findFirst(f -> f.name().equals(name))
				.orElseGet(() -> Feature.missing(name));
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

	interface Feature extends Arrow<Feature>, Named {

		static Feature simple(Name name, Type in, Type out) {
			return new Feature() {
				@Override
				public Name name() {
					return name;
				}

				@Override
				public Vect<Variable> parameters() {
					return Vect.of();
				}

				@Override
				public Vect<Constraint> constraints() {
					return Vect.of();
				}

				@Override
				public Type out() {
					return out;
				}

				@Override
				public Type in() {
					return in;
				}

				@Override
				public String toString() {
					return name.value() + "(" + in + ")" + out;
				}

				@Override
				public <I> Feature with(Visitor<I, Type> v, I in) {
					return this;
				}
			};
		}

		static Feature missing(Name name) {
			return new Feature() {
				@Override
				public Name name() {
					return name;
				}

				@Override
				public Vect<Variable> parameters() {
					return Vect.of();
				}

				@Override
				public Vect<Constraint> constraints() {
					return Vect.of();
				}

				@Override
				public Type out() {
					return Type.Undefined;
				}

				@Override
				public Type in() {
					return Type.Undefined;
				}

				@Override
				public String toString() {
					return name.value() + "(??)??";
				}

				@Override
				public <I> Feature with(Visitor<I, Type> v, I in) {
					return this;
				}
			};
		}
	}

	/** This is not type constructor, just constructor */
	interface Constructor extends Arrow<Constructor>, Named {}

	/**
	 * Arrow is any program element that is being typechecked as
	 * type tranforming arrow. This can be methods,
	 * operators as well as extraction of elements from collection in for comprehension etc.
	 */
	interface Arrow<A extends Arrow<A>> extends Parameterized {
		Type in();
		Type out();

//		A with(Type in, Type out);
//
		<I> A with(Visitor<I, Type> v, I in);
	}

	interface Named {
		Name name();
	}

	interface Variable extends Type, Named {
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.variable(this, in);
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
					return "!" + name + "!";
				}
			};
		}
	}

	interface Typestructor extends Parameterized, Named {
		Vect<Constructor> constructors();
	}

	interface Declared extends Type, Parameterized, Named {
		Vect<Type> arguments();

		Vect<Constructor> constructors();

		/** @param arguments */
		Declared applyArguments(Vect<Type> arguments);

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.declared(this, in);
		}

		default boolean sameDeclaration(Declared d) {
			return name().equals(d.name()); // simplistic definition
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
			return v.variant(this, in);
		}
	}

	@Immutable(singleton = true)
	interface Product extends Structural {
		@Value.Parameter
		Vect<Type> components();

		Product withComponents(Iterable<? extends Type> type);

		class Builder extends ImmutableType.Product.Builder {}

		static Product of(Type... components) {
			return of(Vect.of(components));
		}

		static Product of(Iterable<? extends Type> components) {
			return ImmutableType.Product.of(components);
		}

		@Value.Check
		default void check() {
			checkState(components().size() != 1, "Type system equates tuple of one element to the element itself");
		}

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return this == Empty
					? v.empty(in)
					: v.product(this, in);
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

		Record withFeatures(Iterable<? extends Type.Feature> elements);

		class Builder extends ImmutableType.Record.Builder {}

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.record(this, in);
		}
	}

	interface Function extends Structural, Arrow<Function> {
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.function(this, in);
		}
	}

	@Immutable
	interface Sequence extends Structural {
		// Sequence types are not thought out yet
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.sequence(this, in);
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
			return v.undefined(in);
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
		default O variable(Variable v, I in) {
			return otherwise(v, in);
		}

		default O capture(Capture c, I in) {
			return otherwise(c, in);
		}

		default O declared(Declared d, I in) {
			return otherwise(d, in);
		}

		default O product(Product p, I in) {
			return otherwise(Empty, in);
		}

		default O empty(I in) {
			return otherwise(Empty, in);
		}

		default O record(Record r, I in) {
			return otherwise(r, in);
		}

		default O variant(Variant v, I in) {
			return otherwise(v, in);
		}

		default O sequence(Sequence s, I in) {
			return otherwise(s, in);
		}

		default O function(Function f, I in) {
			return otherwise(f, in);
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

	interface Transformer<I> extends Visitor<I, Type> {

		@Override
		default Type otherwise(Type t, I in) {
			return t;
		}

		@Override
		default Type record(Record r, I in) {
			return r.withFeatures(r.features().map(f -> f.with(this, in)));
		}

		@Override
		default Type product(Product p, I in) {
			return p == Empty ? p : p.withComponents(p.components().map(c -> c.accept(this, in)));
		}
	}
}
