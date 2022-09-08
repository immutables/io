package io.immutables.lang.type;

import com.google.common.base.Joiner;
import io.immutables.collect.Vect;
import io.immutables.lang.type.Type.Declared;
import io.immutables.lang.type.Type.Empty;
import io.immutables.lang.type.Type.Feature;
import io.immutables.lang.type.Type.Parameter;
import io.immutables.lang.type.Type.Product;
import io.immutables.lang.type.Type.Symbol;
import io.immutables.lang.type.Type.Undefined;
import io.immutables.lang.type.Type.Unresolved;
import io.immutables.lang.type.Type.Variable;
import io.immutables.lang.type.Type.Visitor;
import java.util.Arrays;
import java.util.stream.Collectors;

enum Show implements Visitor<Void, String> {
	INSTANCE;

	@Override
	public String empty(Empty e, Void in) {
		return "()";
	}

	@Override
	public String product(Product p, Void in) {
		return "(" + Joiner.on(", ").join(p.components()) + ")";
	}

	@Override
	public String variable(Variable v, Void in) {
		return v.name().value();
	}

	@Override
	public String parameter(Parameter p, Void in) {
		return p.name().value() + (char) ('\u2080' + p.index()); // subscript
	}

	@Override
	public String undefined(Undefined u, Void in) {
		return "\u22A5"; // bottom
	}

	@Override
	public String declared(Declared d, Void in) {
		return parameterized(d.name().value(), d.arguments());
	}

	@Override
	public String unresolved(Unresolved f, Void in) {
		return "-!" + f.name().value() + "!-";
	}

	@Override
	public String otherwise(Type t, Void in) {
		return Arrays.stream(t.getClass().getInterfaces())
				.map(Class::getSimpleName)
				.collect(Collectors.joining(";", "\\", "\\"));
	}

	static String toString(Type type) {
		return type.accept(INSTANCE, null);
	}

	static String toString(Feature f) {
		return parameterized(f.name().value(), f.parameters()) + f.in() + " -> " + f.out();
	}

	static String toString(Symbol symbol) {
		return parameterized(";" + symbol.name().value(), symbol.parameters());
	}

	private static String parameterized(String name, Vect<? extends Type> args) {
		return name + (!args.isEmpty() ? "<" + Joiner.on(", ").join(args) + ">" : "");
	}
}
