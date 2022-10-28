package io.immutables.lang.type;

import java.util.function.Consumer;
import java.util.function.Function;

public interface Constraint {

	void trySolve(Unify.Solution solution);

	void forEachType(Consumer<Type> type);

	Constraint transformTypes(Function<? super Type, ? extends Type> substitution);

	static Constraint equivalence(Type to, Type from) {
		return new Constraint() {
			@Override public void trySolve(Unify.Solution solution) {
				Unify.unify(solution, to, from);
			}

			@Override public void forEachType(Consumer<Type> consumer) {
				consumer.accept(to);
				consumer.accept(from);
			}

			@Override public Constraint transformTypes(Function<? super Type, ? extends Type> substitution) {
				return equivalence(to.transform(substitution), from.transform(substitution));
			}

			@Override public String toString() {
				return to + " == " + from;
			}
		};
	}

	static Constraint conformance(Type expected, Type actual) {
		throw new UnsupportedOperationException();
	}

	static Constraint concept(Type type, Concept concept) {
		throw new UnsupportedOperationException();
	}
}
