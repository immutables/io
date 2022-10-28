package io.immutables.lang.type;

import io.immutables.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

final class Checker {
	enum Resolution { Trivial, Assuming, Impossible, Deferred }

	private final ArrayList<Constraint> effective = new ArrayList<>();
	private final ArrayList<Constraint> eliminated = new ArrayList<>();
	private final IdentityHashMap<Type.Variable, Type> resolved = new IdentityHashMap<>();

	void introduce(Constraint constraint) {
		effective.add(constraint);
	}

	void solve() {
		var c = new SolveControl();
		if (!c.trySolve(effective)) {
			failed = true;
			return;
		}
		c.doSubstitutions();
		effective.clear();
		effective.addAll(c.nextEffective);
	}

	private final class SolveControl implements Unify.Solution {
		final ArrayList<Constraint> nextEffective = new ArrayList<>();
		@Nullable Resolution resolution;

		boolean trySolve(List<Constraint> constraints) {
			for (var a : constraints) {
				resolution = null;
				a.trySolve(this);
				if (resolution == Resolution.Impossible) {
					return false;
				}
			}
			return true; // ??
		}

		void doSubstitutions() {
		}

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

		}

		@Override public void substitute(Type.Variable v, Type with) {
			messages.add("when " + v + " \u27FC " + with);
			if (resolution != Resolution.Impossible) resolution = Resolution.Assuming;
		}

		@Override public void bottom(Type to, Type from) {
			messages.add("mismatch " + to + " </= " + from);
			resolution = Resolution.Impossible;
		}
	}

	private final List<String> messages = new ArrayList<>();
	private boolean failed;

	static void typecheck(Type expected, Type actual) {
		var checker = new Checker();
		checker.introduce(Constraint.equivalence(expected, actual));
		checker.solve();
		boolean trivial = true;

		if (checker.failed) {
			System.out.println(" \u2718  " + expected + " <!= " + actual);
			for (var a : checker.messages) {
				System.out.println("    " + a);
			}
			System.out.println(" ----------------------------------");
			throw new RuntimeException("Type check failed");
		}
		System.out.println(" \u2714  " + expected +
				(trivial ? " = " :" \u225F ") +  actual);
		for (var a : checker.messages) {
			System.out.println("    " + a);
		}
		System.out.println(" ----------------------------------");
	}
}
