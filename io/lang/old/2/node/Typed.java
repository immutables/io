package io.immutables.lang.node;

import java.util.function.Consumer;

class Typed {
	Kind kind;
	String name;
	// host type constructor for parameter
	// origin type constructor for parameterizable and basic
	Tyctor tyctor;
	// reference to parameter from type variable which represents this parameter
	// Typed forParameter;

	// arguments for parameterized, components for product etc
	Typed[] arguments;
	Constraint[] constraints;

	enum Kind {
		Undefined,
		Never,
		/** Type with no type argument */
		Basic,
		Parameterized,
		/**
		 * References scoped type parameter declared as type parameter of the
		 * surrounding feature (method) or type.
		 */
		Parameter,
		/** Type variable that acts as a placeholder to be resolved */
		Variable,

		Product,

		// Implement next

		/** Tagged product */
		Record,
		/** Disjoint(?) union */
		Coproduct,
		/** Nullable type */
		Optional,
	}
/*
	boolean isNominal() {
		return kind == Kind.Basic
				|| kind == Kind.Parameterized;
	}

	boolean isEmpty() {
		return kind == Kind.Product
				&& arguments.length == 0;
	}

	boolean isStructural() {
		switch (kind) {
		case Product:
		case Record:
		case Coproduct:
		case Optional:
			return true;
		default:
			return false;
		}
	}
*/
	boolean isUnique() {
		switch (kind) {
		case Undefined:
		case Never:
		case Basic:
			return true;
		case Product:
			return arguments.length == 0;
		default:
			return false;
		}
	}

	Typed cloneOrUnique(Consumer<Typed> transform) {
		if (isUnique()) return this;

		var c = new Typed();
		c.name = name;
		c.kind = kind;
		c.tyctor = tyctor;
		c.constraints = constraints;
		transform.accept(c);
		if (arguments != null) {
			c.arguments = arguments.clone();
			for (var i = 0; i < arguments.length; i++) {
				c.arguments[i] = arguments[i].cloneOrUnique(transform);
			}
		}
		if (constraints != null) {
			c.constraints = constraints.clone();
			// TODO not sure clone or not and how to replace etc.
		}
		return c;
	}

	@Override public String toString() {
		return Types.show(this);
	}
}
