package io.immutables.lang.type;

import static io.immutables.lang.type.TypeIf.*;
import static io.immutables.lang.type.Types.occurs;

@SuppressWarnings("StatementWithEmptyBody")
class Unify {
	private Unify() {}

	interface Solution {
		void trivial();
		void recursive(Type.Variable v, Type in);
		void alias(Type.Variable v, Type.Variable from);
		void substitute(Type.Variable v, Type with);
		void bottom(Type to, Type from);
	}

	static void unify(Solution solution, Type to, Type from) {
		if (to.equals(from)) solution.trivial();
		else if (Variable(to, v -> unifyVariable(solution, v, from))) ;
		else if (Variable(from, v -> unifyVariable(solution, v, to))) ;
		else if (Terminal(to, t -> unifyToTerminal(solution, t, from))) ;
		else if (Applied(to, a -> unifyToApplied(solution, a, from))) ;
		else if (Product(to, p -> unifyToProduct(solution, p, from))) ;
	}

	static void unifyVariable(Solution solution, Type.Variable v, Type against) {
		// v == against will not be ever true if expected.equals(actual) check would occur in `unify`
		// but this makes this function `unifyVariable` self-sufficient
		if (v == against) solution.trivial();
		else if (Variable(against, a -> solution.alias(v, a))) ;
		else if (occurs(v, against)) solution.recursive(v, against);
		else solution.substitute(v, against);
	}

	// this gets a chance to run type coercions, otherwise expected.equals(actual) would
	// make it will never run or always mismatch
	private static void unifyToTerminal(Solution solution, Type.Terminal t, Type from) {
		solution.bottom(t, from); // add conformance coercions before else
	}

	private static void unifyToApplied(Solution solution, Type.Applied a, Type from) {
		if (Applied(from, f -> {
			if (a.constructor() == f.constructor() && pairwise(a.arguments, f.arguments,
					(aa, fa) -> unify(solution, aa, fa))) ;
			else solution.bottom(a, f);
		})) ;
		else solution.bottom(a, from); // add conformance coercions before else
	}

	// this gets a chance to run type coercions, otherwise expected.equals(actual) would
	// make it will never run or always mismatch
	private static void unifyToProduct(Solution solution, Type.Product p, Type from) {
		if (Product(from, f -> {
			if (pairwise(p.components(), f.components(),
					(pc, fc) -> unify(solution, pc, fc))) ;
			else solution.bottom(p, from);
		})) ;
		else solution.bottom(p, from); // add conformance coercions before else
	}
}
