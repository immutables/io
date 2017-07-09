package io.immutables.lang.type;

import io.immutables.collect.Vect;
import io.immutables.lang.type.Type.Arrow;
import io.immutables.lang.type.Type.Declared;
import io.immutables.lang.type.Type.Variable;

final class Typecheck {
	private Typecheck() {}

	static Arrow apply(Arrow arrow, Type in, Type out) {
		Type arrowIn = arrow.in();
		Type arrowOut = arrow.out();

		Check check = new Check(arrow.parameters());
		Type transformedIn = arrow.in().accept(check, in);

		// TODO transform outs
		return arrow.with(transformedIn, arrowOut);
	}

	static class Check implements Type.Transformer<Type> {
		private final Vect<Variable> parameters;

		Check(Vect<Variable> parameters) {
			this.parameters = parameters;
		}

		@Override
		public Type variable(Type in, Variable v) {
			return v;
		}

		@Override
		public Type declared(Type in, Declared d) {
			return d;
		}
	}
}
