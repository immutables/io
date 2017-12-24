package io.immutables.lang.type.fixture;

import io.immutables.collect.Vect;
import io.immutables.lang.type.Type22;
import io.immutables.lang.type.Type22.Arrow;
import io.immutables.lang.type.Type22.Declared;
import io.immutables.lang.type.Type22.Variable;

final class Typecheck {
	private Typecheck() {}

	static Arrow apply(Arrow arrow, Type22 in, Type22 out) {
		Type22 arrowIn = arrow.in();
		Type22 arrowOut = arrow.out();

		Check check = new Check(arrow.parameters());
		Type22 transformedIn = arrow.in().accept(check, in);

		// TODO transform outs
		return null;// arrow.with(transformedIn, arrowOut);
	}

	static class Check implements Type22.Transformer<Type22> {
		private final Vect<Variable> parameters;

		Check(Vect<Variable> parameters) {
			this.parameters = parameters;
		}

		@Override
		public Type22 variable(Variable v, Type22 in) {
			return v;
		}

		@Override
		public Type22 declared(Declared d, Type22 in) {
			return d;
		}
	}
}
