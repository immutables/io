package io.immutables.lang.type;

import io.immutables.collect.Vect;
import java.util.function.Function;

public abstract class Type {

	/** Type parameter as declared in signature. */
	public static final class Parameter extends Type implements Named {
		private final String name;
		public final int index;

		private Parameter(String name, int index) {
			this.name = name;
			this.index = index;
		}

		@Override public String name() {return name;}

		@Override public String toString() {return name + Character.toString(0x2080 + index) + "\u0302";}

		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.parameter(this, in);
		}

		static Parameter introduce(String name, int index) {
			return new Parameter(name, index);
		}
	}

	public static final class Variable extends Type implements Named {
		private final String name;

		private Variable(String name) {this.name = name;}

		@Override public String name() {return name;}

		@Override public String toString() {return name + "\u2032";}

		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.variable(this, in);
		}

		static Variable allocate(String name) {
			return new Variable(name);
		}
	}

	public static abstract class Nominal extends Type implements Named {
		public abstract TypeConstructor constructor();
	}

	public static final class Terminal extends Nominal implements TypeConstructor {
		private final String name;

		private Terminal(String name) {this.name = name;}

		@Override public String name() {return name;}

		@Override public Vect<Parameter> parameters() {return Vect.of();}

		@Override public TypeConstructor constructor() {return this;}

		@Override public Nominal instantiate(Vect<Type> arguments) {
			assert arguments.isEmpty();
			return this;
		}

		@Override public String toString() {
			return name;
		}

		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.terminal(this, in);
		}

		public static Terminal define(String name) {
			return new Terminal(name);
		}
	}

	public static final class Applied extends Nominal {
		private final TypeConstructor constructor;
		public final Vect<Type> arguments;

		private Applied(TypeConstructor constructor, Vect<Type> arguments) {
			this.constructor = constructor;
			this.arguments = arguments;
		}

		@Override public String name() {return constructor.name();}

		@Override public TypeConstructor constructor() {return constructor;}

		@Override public boolean equals(Object obj) {
			return this == obj || (obj instanceof Applied
					&& ((Applied) obj).constructor == constructor
					&& ((Applied) obj).arguments.equals(arguments));
		}

		@Override public int hashCode() {
			return constructor.hashCode() + arguments.hashCode();
		}

		@Override public String toString() {
			return constructor.name() + arguments.join(", ", "<", ">");
		}

		@Override public Type transform(Function<? super Type, ? extends Type> substitution) {
			var t = substitution.apply(this);
			return t != this ? t : instance(constructor, arguments.map(substitution));
		}

		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.applied(this, in);
		}

		static Applied instance(TypeConstructor constructor, Vect<Type> arguments) {
			return new Applied(constructor, arguments);
		}
	}

	public static abstract class Structural extends Type {
		protected final Vect<Type> components;

		protected Structural(Vect<Type> components) {
			this.components = components;
		}

		@Override public boolean equals(Object o) {
			if (this == o) return true;
			if (o.getClass() != getClass()) return false;
			return ((Structural) o).components.equals(components);
		}

		@Override public int hashCode() {
			return getClass().hashCode() + components.hashCode();
		}
	}

	public static final class Product extends Structural {
		private Product(Vect<Type> components) {
			super(components);
		}

		public Vect<Type> components() {return components;}

		public static Product of(Vect<Type> components) {
			if (components.isEmpty()) return Empty;
			assert components.size() >= 2;
			return new Product(components);
		}

		public static Product of(Type c0, Type c1, Type... components) {
			return new Product(Vect.<Type>builder()
					.add(c0).add(c1)
					.addAll(components)
					.build());
		}

		@Override public String toString() {
			return components.join(", ", "(", ")");
		}

		@Override public Type transform(Function<? super Type, ? extends Type> substitution) {
			var t = substitution.apply(this);
			return t != this || t == Empty ? t : of(components.map(substitution));
		}

		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return this == Empty ? v.empty(in) : v.product(this, in);
		}

		public static final Product Empty = new Product(Vect.of());
	}

	public <I, O> O accept(Visitor<I, O> v, I in) {
		return v.otherwise(this, in);
	}

	public Type transform(Function<? super Type, ? extends Type> substitution) {
		return substitution.apply(this);
	}

	public interface Visitor<I, O> {
		default O parameter(Parameter d, I in) {
			return otherwise(d, in);
		}

		default O variable(Variable v, I in) {
			return otherwise(v, in);
		}

		default O terminal(Terminal b, I in) {
			return otherwise(b, in);
		}

		default O applied(Applied d, I in) {
			return otherwise(d, in);
		}

		default O product(Product p, I in) {
			return otherwise(p, in);
		}

		default O empty(I in) {
			return otherwise(Product.Empty, in);
		}

		default O otherwise(Type t, I in) {
			throw new UnsupportedOperationException("cannot handle type " + t + " with input: " + in);
		}
	}
}
