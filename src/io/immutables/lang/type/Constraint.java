package io.immutables.lang.type;

import io.immutables.collect.Vect;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Enclosing
interface Constraint {
	// T == Opt<V>
	@Immutable
	interface Equivalence extends Constraint {
		abstract @Parameter Type left();
		abstract @Parameter Type right();

		static Equivalence of(Type left, Type right) {
			return ImmutableConstraint.Equivalence.of(left, right);
		}
	}

	// S : Concept<A>
	@Immutable
	interface Context extends Constraint {
		// TODO reintroduce self?
		// abstract @Parameter Type self();
		abstract @Parameter Type.Concept concept();
		abstract @Parameter Vect<Type> arguments();

		static Context of(Type.Concept concept, Iterable<Type> arguments) {
			return ImmutableConstraint.Context.of(concept, arguments);
		}
	}

	// T(a A, b B)
	@Immutable
	interface InstanceConstruction extends Constraint {
		abstract @Parameter Type.Variable self();
		abstract @Parameter Type in();
	}

	// V : { a A, b B }
	@Immutable
	interface StructuralDecomposition extends Constraint {
		abstract @Parameter Type self();
	}
}
