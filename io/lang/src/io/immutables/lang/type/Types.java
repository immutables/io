package io.immutables.lang.type;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

class Types {
	private Types() {}

	static Function<Type, Type> substitution(Map<? extends Type, ? extends Type> map) {
		return t -> {
			var v = map.get(t);
			return v != null ? v : t;
		};
	}

	static Function<Type, Type> substitution(Type.Parameter parameter, Type replacement) {
		return replacementByRef(parameter, replacement);
	}

	static Function<Type, Type> substitution(Type.Variable variable, Type replacement) {
		return replacementByRef(variable, replacement);
	}

	private static Function<Type, Type> replacementByRef(Type r, Type t) {
		return type -> r == type ? t : type;
	}

	private static final class Traversal implements Type.Visitor<Void, Void> {
		private final Consumer<? super Type> before;
		private final Consumer<? super Type> after;

		private Traversal(Consumer<? super Type> before, Consumer<? super Type> after) {
			this.before = before;
			this.after = after;
		}

		@Override public Void otherwise(Type t, Void in) {
			before.accept(t);
			after.accept(t);
			return null;
		}

		@Override public Void applied(Type.Applied p, Void in) {
			before.accept(p);
			p.arguments.forEach(a -> a.accept(this, null));
			after.accept(p);
			return null;
		}

		@Override public Void product(Type.Product p, Void in) {
			before.accept(p);
			p.components.forEach(c -> c.accept(this, null));
			after.accept(p);
			return null;
		}
	}

	static void traverse(Type type, Consumer<? super Type> before, Consumer<? super Type> after) {
		type.accept(new Traversal(before, after), null);
	}

	static void traverse(Type type, Consumer<? super Type> before) {
		traverse(type, before, t -> {});
	}

	static boolean occurs(Type.Variable variable, Type in) {
		class Finder implements Consumer<Type> {
			boolean occurs;
			@Override public void accept(Type type) {
				if (type == variable) occurs = true;
			}
		}
		var f = new Finder();
		traverse(in, f);
		return f.occurs;
	}

/*
	enum Kind {
		Parameter,
		Variable,
		Simple,
		Parameterized,
		Product,
	}
*/
/*
	static final class VariablesForParameters {
		private final Map<Type.Parameter, Type> substitutions = new IdentityHashMap<>();

		final Vect<Type.Parameter> parameters;
		final Vect<Type.Variable> variables;

		VariablesForParameters(Parameterizable parameterizable) {
			this.parameters = parameterizable.parameters();
			this.variables = parameters.map(p -> Type.Variable.allocate(p.name()));
			this.parameters.forEach(p -> substitutions.put(p, variables.get(p.index)));
		}

		Type substitute(Type type) {
			return type.transform(t -> t instanceof Type.Parameter ?
					substitutions.getOrDefault(t, t) : t);
		}

		static VariablesForParameters sample(String... name) {
			var parameters = Vect.of(name).mapIndex((i, n) -> Type.Parameter.introduce(n, i));
			return new VariablesForParameters(() -> parameters);
		}
	}*/
}
