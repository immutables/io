package io.immutables.lang.type;

import io.immutables.collect.Vect;

final class Productions {
	private Productions() {}

	static Production.Access access(String name) {
		var a = new Production.Access();
		a.name = name;
		return a;
	}

	static Production.FeatureApply plus(Production.Expression a, Production.Expression b) {
		return apply(a, "+", b);
	}

	static Production.NumberLiteral number(int value) {
		var n = new Production.NumberLiteral();
		n.value = value;
		return n;
	}

	static Production.FeatureApply apply(Production.Expression on, String name, Production.Expression in0, Production.Expression in1, Production.Expression... ins) {
		return apply(on, name, product(in0, in1, ins));
	}

	static Production.Product product(Production.Expression component0, Production.Expression component1, Production.Expression... components) {
		var p = new Production.Product();
		p.components = Vect.<Production.Expression>builder()
				.add(component0)
				.add(component1)
				.addAll(components)
				.build();
		return p;
	}

	Production.Product Empty = newEmpty();

	private static Production.Product newEmpty() {
		var p = new Production.Product();
		p.components = Vect.of();
		return p;
	}

	static Production.FeatureApply apply(Production.Expression on, String name, Production.Expression in) {
		var a = new Production.FeatureApply();
		a.name = name;
		a.on = on;
		a.in = in;
		return a;
	}
}
