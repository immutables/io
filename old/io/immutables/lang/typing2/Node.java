package io.immutables.lang.typing2;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import io.immutables.grammar.Symbol;

/** Typed and distilled computational node. */
public interface Node {
	/** Source code (CST) production. */
	int production();
	/** Resulting type. */
	Type type();

	/**
	 * Tries to conform this to expected type. If fully conforms, {@code this} node can be returned.
	 * Otherwise transformed or wrapping node will be returned. If Node do not conform, special
	 * unmatching node would be returned. Usually such unmaching node will have undefined result type.
	 * Expected type cannot be undefined, if expected type is not defined, the
	 * {@link #conforming(Type)} method should not be called and {@code this} node should be used as
	 * is.
	 */
	Node conforming(Type expected);

	interface Empty extends Node {
		static Empty empty(int production) {
			return new Empty() {
				@Override
				public int production() {
					return production;
				}

				@Override
				public Type type() {
					return Type.Empty;
				}

				@Override
				public Node conforming(Type expected) {
					throw new UnsupportedOperationException();
				}

				@Override
				public String toString() {
					return "()";
				}
			};
		}
	}

	interface Nonconforming extends Node {
		Node from();
		Type expected();

		static Nonconforming from(Node node, Type expected) {
			return new Nonconforming() {
				@Override
				public int production() {
					return node.production();
				}

				@Override
				public Type type() {
					return Type.Undefined;
				}

				@Override
				public Node from() {
					return node;
				}

				@Override
				public Type expected() {
					return expected;
				}

				@Override
				public Node conforming(Type expected) {
					return this;
				}
			};
		}
	}

	interface ConstructProduct extends Node {
		static ConstructProduct of(int production, Node... components) {
			return new ConstructProduct() {
				@Override
				public int production() {
					return production;
				}

				@Override
				public Type type() {
					return null;
				}

				@Override
				public String toString() {
					return "(" + Joiner.on(", ").join(components) + ")";// + ": " + type;
				}

				@Override
				public Node conforming(Type expected) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	interface StaticValue extends Node {
		static Node.StaticValue of(int production, Object value, Type type) {
			return new StaticValue() {
				@Override
				public int production() {
					return production;
				}

				@Override
				public Type type() {
					return type;
				}

				@Override
				public String toString() {
					return String.valueOf(value) + ": " + type;
				}

				@Override
				public Node conforming(Type expected) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	static void tryApplyFeature(int production, Node receiver, Symbol name, Supplier<Node> input) {
		Type.Feature feature = receiver.type().getFeature(name);
		if (feature.isDefined()) {
			Checking.Matcher matcher = new Checking.Matcher(feature.parameters());
			Node node = input.get();
//			matcher.match(expect, null);
//			ApplyFeature.of(production, receiver, feature, , receiver, null);
		}
	}

	interface UnapplicableFeature extends Node {
		static UnapplicableFeature of(
				int production,
				Node receiver,
				Type.Feature feature,
				Node argument) {
			return new UnapplicableFeature() {

				@Override
				public int production() {
					return production;
				}

				@Override
				public Type type() {
					return Type.Undefined;
				}

				@Override
				public String toString() {
					return receiver + "." + feature + " <~/~ " + argument;
				}

				@Override
				public Node conforming(Type expected) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	interface ApplyFeature extends Node {
		Type[] arguments();
		Node receiver();
		Type.Feature feature();
		Node input();

		static ApplyFeature of(
				int production,
				Node receiver,
				Type.Feature feature,
				Type[] arguments,
				Node input,
				Type out) {
			return new ApplyFeature() {
				@Override
				public int production() {
					return production;
				}

				@Override
				public Node input() {
					return input;
				}

				@Override
				public Type[] arguments() {
					return arguments;
				}

				@Override
				public Type.Feature feature() {
					return feature;
				}

				@Override
				public Type type() {
					return out;
				}

				@Override
				public String toString() {
					return receiver + "." + feature.name()
							+ (feature.in() == Type.Empty ? "" : ("(" + input + ")")) + ": " + out;
				}

				@Override
				public Node conforming(Type expected) {
					throw new UnsupportedOperationException();
				}

				@Override
				public Node receiver() {
					return receiver;
				}
			};
		}
	}
}
