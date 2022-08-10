package io.immutables.lang.node;

import java.util.NoSuchElementException;
import static io.immutables.lang.node.Typed.Kind.Basic;
import static io.immutables.lang.node.Typed.Kind.Parameter;
import static io.immutables.lang.node.Typed.Kind.Parameterized;

class Tyctor {
	final String name;
	final Typed[] parameters;
	final Constraint[] constraints;

	Tyctor(String name, Typed[] parameters, Constraint[] constraints) {
		this.name = name;
		this.parameters = parameters;
		this.constraints = constraints;

		for (var p : parameters) {
			assert p.kind == Parameter;
			p.constraints = constraints;
			p.tyctor = this;
		}
	}

	Typed getParameter(String name) {
		for (var p : parameters) {
			if (p.name.equals(name)) return p;
		}
		throw new NoSuchElementException("parameter " + name);
	}

	private Typed instance;

	Typed instance(Typed... arguments) {
		if (arguments.length != parameters.length) {
			throw new IllegalArgumentException("arguments doesn't much parameters in number");
		}
		if (parameters.length == 0) {
			if (instance != null) return instance;
			var t = new Typed();
			t.kind = Basic;
			t.tyctor = this;
			t.name = name;
			return instance = t;
		}

		var t = new Typed();
		t.kind = Parameterized;
		t.tyctor = this;
		t.name = name;
		t.arguments = arguments;

		for (var i = 0; i < parameters.length; i++) {
			var p = parameters[i];
			var a = arguments[i];
			// check if a applicable to p
		}
		// and no constraint checks
		return t;
	}

	static Tyctor basic(String name) {
		return new Tyctor(name, Types.NO_TYPES, Types.NO_CONSTRAINTS);
	}

	static Tyctor parameterized(String name, String... parameters) {
		assert parameters.length > 0;
		var types = new Typed[parameters.length];
		for (var i = 0; i < parameters.length; i++) {
			var t = new Typed();
			t.kind = Parameter;
			t.name = parameters[i];
			types[i] = t;
		}
		return new Tyctor(name, types, Types.NO_CONSTRAINTS);
	}
}
