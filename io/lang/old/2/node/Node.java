package io.immutables.lang.node;

import io.immutables.Nullable;
import io.immutables.collect.Vect;
import io.immutables.grammar.Escapes;

abstract class Node {
	/** index of syntax production this node represents. */
	// int production;

	/** Type of this node */
	Typed type;

	interface Traversal {
		void apply(Node node);
	}

	void traverse(Traversal before, Traversal after) {
		before.apply(this);
		after.apply(this);
	}

	static class ConstructProduct extends Node {
		Node[] components;

		@Override public String toString() {
			return Vect.of(components).join(", ", "(", ")");
		}

		void traverse(Traversal before, Traversal after) {
			before.apply(this);
			for (var c : components) c.traverse(before, after);
			after.apply(this);
		}
	}

	static class ApplyFeature extends Node {
		String name;
		FeatureArrow arrow;
		@Nullable Node on;
		@Nullable Node in;

		@Override public String toString() {
			return Nodes.showFeature(this);
		}

		void traverse(Traversal before, Traversal after) {
			before.apply(this);
			if (on != null) on.traverse(before, after);
			if (in != null) in.traverse(before, after);
			after.apply(this);
		}
	}

	static class IntLiteral extends Node {
		{
			this.type = BuiltinTypes.i32;
		}
		int value;

		@Override public String toString() {
			return Integer.toString(value);
		}
	}

	static class StrLiteral extends Node {
		{
			this.type = BuiltinTypes.Str;
		}
		String value;

		@Override public String toString() {
			return Escapes.doubleQuote(value);
		}
	}
}
