package io.immutables.lang.type.fixture;

import com.google.common.base.Joiner;
import io.immutables.lang.type.Type22;

interface Node {
	default boolean checks() {
		return false;
	}
	default Type22 type() {
		return Type22.Undefined;
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
		public Type22 type() {
			return Type22.Empty;
		}

		@Override
		public boolean checks() {
			return true;
		}

		@Override
		public String toString() {
			return "()";
		}
	};

	Node NotApplicable = new Node() {
		@Override
		public Type22 type() {
			return Type22.Undefined;
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
				Type22.Feature feature,
				Node argument) {
//			Type in = feature.in();
//			Type out = feature.out();
			return new UnapplicableFeature() {

				@Override
				public String toString() {
					return receiver + "." + feature + " <~/~ " + argument;
				}
			};
		}
	}

	interface Scoped extends Node {}

	interface ApplyFeature extends Node {
		static Node.ApplyFeature of(
				int production,
				Node receiver,
				Type22.Feature feature,
				Node argument) {
//				Type in = feature.in();
//				Type out = feature.out();
			return new ApplyFeature() {
				@Override
				public Type22 type() {
					return feature.out();
				}
				@Override
				public boolean checks() {
					return true;
				}
				@Override
				public String toString() {
					return receiver + "." + feature.name()
							+ (feature.in() == Type22.Empty ? "" : ("(" + argument + ")")) + ": " + type();
				}
			};
		}
	}

	interface StaticValue extends Node {
		static Node.StaticValue of(int production, Object value, Type22 type) {
			return new StaticValue() {
				@Override
				public Type22 type() {
					return type;
				}
				@Override
				public boolean checks() {
					return true;
				}
				@Override
				public String toString() {
					return String.valueOf(value) + ": " + type;
				}
			};
		}
	}

	interface TypeMismatch extends Node {
		static TypeMismatch of(int production, Type22 expected) {
			return new TypeMismatch() {
				@Override
				public String toString() {
					return "(!:" + expected + ")";
				}
			};
		}
	}

	interface ConstructProduct extends Node {
		static Node.ConstructProduct of(int production, Type22 type, Node... components) {
			return new ConstructProduct() {
				@Override
				public Type22 type() {
					return type;
				}
				@Override
				public boolean checks() {
					return true;
				}
				@Override
				public String toString() {
					return "(" + Joiner.on(", ").join(components) + "): " + type;
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
		static StructuralMismatch of(int production, Type22 expected) {
			return new StructuralMismatch() {
				@Override
				public String toString() {
					return " != " + expected;
				}
			};
		}
	}

	interface ApplyConstructor extends Node {

	}

	interface ConstructRecord extends Node {

	}
}
