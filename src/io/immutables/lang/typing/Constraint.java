package io.immutables.lang.typing;

public interface Constraint {
	<I, O> O accept(Visitor<I, O> visitor, I in);

	interface Visitor<I, O> {
		O equivalence(Type left, Type right, I in);
		O construction(Type.Parameter parameter, Type input, I in);
	}
	
	// T(a A, b B)
	interface Construction {
//		@Override
//		default <I, O> O accept(Visitor<I, O> visitor, I in) {
//			return visitor.construction(variable, input, in);
//		}
	}

	// T == Opt<V>
	interface Equivalence {
//		@Override
//		default <I, O> O accept(Visitor<I, O> visitor, I in) {
//			return visitor.equivalence(left(), right(), in);
//		}
	}
}
