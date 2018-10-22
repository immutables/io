package io.immutables.lang.type22;

import io.immutables.collect.Vect;
import io.immutables.lang.type22.ImmutableConstraint22;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Enclosing
interface Constraint22 {
	// T == Opt<V>
	@Immutable
	interface Equivalence extends Constraint22 {
		abstract @Parameter Type22 left();
		abstract @Parameter Type22 right();

		static Equivalence of(Type22 left, Type22 right) {
			return ImmutableConstraint22.Equivalence.of(left, right);
		}
	}

	// S : Concept<A>
	@Immutable
	interface Context extends Constraint22 {
		// TODO reintroduce self?
		// abstract @Parameter Type self();
		abstract @Parameter Type22.Concept concept();
		abstract @Parameter Vect<Type22> arguments();

		static Context of(Type22.Concept concept, Iterable<Type22> arguments) {
			return ImmutableConstraint22.Context.of(concept, arguments);
		}
	}

	// T(a A, b B)
	@Immutable
	interface InstanceConstruction extends Constraint22 {
		abstract @Parameter Type22.Variable self();
		abstract @Parameter Type22 in();
	}

	// V : { a A, b B }
	@Immutable
	interface StructuralDecomposition extends Constraint22 {
		abstract @Parameter Type22 self();
	}
}
