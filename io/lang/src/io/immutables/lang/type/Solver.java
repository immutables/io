package io.immutables.lang.type;

import io.immutables.Nullable;
import io.immutables.lang.type.Unify.Solution;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import static io.immutables.lang.type.TypeIf.*;
import static io.immutables.lang.type.Types.traverse;
import static java.util.Collections.newSetFromMap;
import static java.util.Objects.requireNonNullElse;

@Deprecated
public class Solver {
	final List<String> messages = new ArrayList<>();
	private @Nullable Resolution resolution;

	enum Resolution { Trivial, Assuming, Impossible, Deferred }

	interface Proposition {
		void collectVariables(Consumer<Type.Variable> consumer);
		Proposition transform(Function<Type, Type> substitution);
		void solve(Solution solution);
	}

	final Set<Proposition> eliminated = newSetFromMap(new IdentityHashMap<>());
	final Set<Proposition> active = newSetFromMap(new IdentityHashMap<>());
	final Map<Type.Variable, Type> substitutions = new IdentityHashMap<>();
	final Map<Type.Variable, Type> resolutions = new IdentityHashMap<>();
	final Set<Type.Variable> unresolved = newSetFromMap(new IdentityHashMap<>());

	private boolean hasSomethingChanged;

	static Solver solver(Proposition... propositions) {
		var solver = new Solver();
		for (var p : propositions) solver.introduce(p);
		return solver;
	}

	private void introduce(Proposition p) {
		active.add(p);
		p.collectVariables(unresolved::add);
		hasSomethingChanged = true;
	}

	boolean solve() {
		while (hasSomethingChanged) {
			hasSomethingChanged = false;

			for (var a : List.copyOf(active)) {
				var solution = new Control();
				a.solve(solution);

				assert solution.resolution != null;
				resolution = solution.resolution;
				switch (solution.resolution) {
				case Impossible:
					return false;
				case Assuming:
				case Trivial:
					active.remove(a);
					eliminated.add(a);
					hasSomethingChanged = true;
				}
			}

			if (!substitutions.isEmpty() && !active.isEmpty()) {
				for (var a : List.copyOf(active)) {
					// it will be changed most of the time as of now if structural/applied types used,
					// but it can be optimized later
					var transformed = a.transform(Types.substitution(substitutions));
					if (transformed != a) {
						eliminated.add(a);
						active.remove(a);
						active.add(transformed);
						hasSomethingChanged = true;
					}
				}
				substitutions.clear();
			}
		}
		return active.isEmpty();
	}

	static Proposition conforms(Type expected, Type actual) {
		return new Proposition() {
			@Override public void collectVariables(Consumer<Type.Variable> consumer) {
				traverse(expected, t -> Variable(t, consumer));
				traverse(actual, t -> Variable(t, consumer));
			}

			@Override public Proposition transform(Function<Type, Type> substitution) {
				return conforms(
						expected.transform(substitution),
						actual.transform(substitution));
			}

			@Override public void solve(Solution solution) {
				Unify.unify(solution, expected, actual);
			}

			@Override public String toString() {
				return ":- " + expected + " == " + actual;
			}
		};
	}

	class Control implements Solution {
		@Nullable Resolution resolution;

		@Override public void trivial() {
			if (resolution == null) resolution = Resolution.Trivial;
		}

		@Override public void recursive(Type.Variable v, Type in) {
			messages.add("recursive " + v + " :in: " + in);
			resolution = Resolution.Impossible;
		}

		@Override public void alias(Type.Variable v, Type.Variable from) {
			messages.add("alias " + v + " \u27FC " + from);
			if (resolution != Resolution.Impossible) resolution = Resolution.Assuming;
			// TODO alias is trick if goes both ways
			//substitutions.put(v, from);
		}

		@Override public void substitute(Type.Variable v, Type with) {
			messages.add("when " + v + " \u27FC " + with);
			if (resolution != Resolution.Impossible) resolution = Resolution.Assuming;

			//substitutions.put(v, with);
		}

		@Override public void bottom(Type to, Type from) {
			messages.add("mismatch " + to + " </= " + from);
			resolution = Resolution.Impossible;
		}
	}

	static void typecheck(Type expected, Type actual) {
		var solver = solver(conforms(expected, actual));
		solver.solve();
		var r = requireNonNullElse(solver.resolution, Solver.Resolution.Impossible);

		// var solver = new Solver();
		// var control = solver.new Control();
		// Unify.unify(control, expected, actual);
		// var r = requireNonNullElse(control.resolution, Solver.Resolution.Impossible);

		if (r == Solver.Resolution.Impossible) {
			System.out.println(" \u2718  " + expected + " <!= " + actual);
			for (var a : solver.messages) {
				System.out.println("    " + a);
			}
			System.out.println(" ----------------------------------");
			throw new RuntimeException("Type check failed");
		}
		System.out.println(" \u2714  " + expected +
				(r == Solver.Resolution.Trivial ? " = " :" \u225F ") +  actual);
		for (var a : solver.messages) {
			System.out.println("    " + a);
		}
		System.out.println(" ----------------------------------");
	}
}
