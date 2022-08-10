package io.immutables.lang.node;

import io.immutables.collect.Vect;

final class Types {
	static Typed empty() {
		var t = new Typed();
		t.kind = Typed.Kind.Product;
		t.arguments = NO_TYPES;
		return t;
	}

	static Typed product(Typed c0, Typed c1, Typed... cs) {
		var t = new Typed();
		t.kind = Typed.Kind.Product;
		t.arguments = new Typed[2 + cs.length];
		t.arguments[0] = c0;
		t.arguments[1] = c1;
		System.arraycopy(cs, 0, t.arguments, 2, cs.length);
		return t;
	}

	static String show(Typed typed) {
		switch (typed.kind) {
		case Basic: return typed.name;
		case Parameterized: return typed.name + Vect.of(typed.arguments).map(Types::show).join(",", "<", ">");
		case Parameter: return "<" + typed.name + ">";
		case Variable: return "'" + typed.name;
		case Product: return Vect.of(typed.arguments).map(Types::show).join(",", "(", ")");
		default: return typed.kind + "!?";
		}
	}

	static String show(FeatureArrow feature) {
		if (feature.missing) {
			return ".!-" + feature.name + "-!" + feature.in;
		}
		var isOperator = "+".equals(feature.name);
		if (isOperator) {
			return "(" + feature.on + ") " + feature.name + " (" + feature.in + ")";
		}
		return "(" + feature.on + ")." + feature.name + " (" + feature.in + ")";
	}

	//static final String UNDEFINED = "_";
	static final Typed[] NO_TYPES = {};
	static final Constraint[] NO_CONSTRAINTS = {};
}
