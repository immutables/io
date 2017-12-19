package io.immutables.lang.type.fixture;

import com.google.common.base.Joiner;
import io.immutables.lang.type.Type;

interface Node {
	default Type type() {
		return Type.Undefined;
	}

//		interface ExprectedType extends Node {
//			static ExprectedType of(Type type) {
//				return new ExprectedType() {
//					@Override
//					public Type type() {
//						return type;
//					}
//				};
//			}
//		}

	Node Empty = new Node() {
		@Override
		public Type type() {
			return Type.Empty;
		}

		@Override
		public String toString() {
			return "()";
		}
	};

	Node NotApplicable = new Node() {
		@Override
		public Type type() {
			return Type.Undefined;
		}

		@Override
		public String toString() {
			return "<NA>";
		}
	};

	interface UnapplicableFeature extends Node {
		static Node.UnapplicableFeature of(
				int production,
				Node receiver,
				Type.Feature feature,
				Node argument) {
//			Type in = feature.in();
//			Type out = feature.out();
			return new UnapplicableFeature() {

				@Override
				public String toString() {
					return feature + " ~!~> " + argument;
				}
			};
		}
	}

	interface Scoped extends Node {}

	interface ApplyFeature extends Node {
		static Node.ApplyFeature of(
				int production,
				Node receiver,
				Type.Feature feature,
				Node argument) {
//				Type in = feature.in();
//				Type out = feature.out();
			return new ApplyFeature() {
				@Override
				public Type type() {
					return ApplyFeature.super.type();
				}
				@Override
				public String toString() {
					return receiver + "." + feature.name() + "(" + argument + ")";
				}
			};
		}
	}

	interface StaticValue extends Node {
		static Node.StaticValue of(int production, Object value, Type type) {
			return new StaticValue() {
				@Override
				public Type type() {
					return type;
				}
				@Override
				public String toString() {
					return String.valueOf(value);
				}
			};
		}
	}

	interface TypeMismatch extends Node {
		static TypeMismatch of(int production, Type expected) {
			return new TypeMismatch() {
				@Override
				public String toString() {
					return "(!:" + expected + ")";
				}
			};
		}
	}

	interface ConstructProduct extends Node {
		static Node.ConstructProduct of(int production, Type type, Node... components) {
			return new ConstructProduct() {
				@Override
				public Type type() {
					return type;
				}
				@Override
				public String toString() {
					return "(" + Joiner.on(", ").join(components) + ")";
				}
			};
		}
	}

	interface LetBindingReference extends Node {
		static LetBindingReference of(Node referenced) {
			return new LetBindingReference() {

			};
		}
	}

	interface StructuralMismatch extends Node {
		static StructuralMismatch of(int production) {
			return new StructuralMismatch() {
				@Override
				public String toString() {
					return "!<>!";
				}
			};
		}
	}

	interface ApplyConstructor extends Node {

	}

	interface ConstructRecord extends Node {

	}
}
