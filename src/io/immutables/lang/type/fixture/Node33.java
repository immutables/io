package io.immutables.lang.type.fixture;

import com.google.common.base.Joiner;
import io.immutables.lang.type.Type22;

interface Node33 {
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

	Node33 Empty = new Node33() {
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

	Node33 NotApplicable = new Node33() {
		@Override
		public Type22 type() {
			return Type22.Undefined;
		}

		@Override
		public String toString() {
			return "<NA>";
		}
	};

	interface UnapplicableFeature extends Node33 {
		static Node33.UnapplicableFeature of(
				int production,
				Node33 receiver,
				Type22.Feature feature,
				Node33 argument) {
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

	interface Scoped extends Node33 {}

	interface ApplyFeature extends Node33 {
		static Node33.ApplyFeature of(
				int production,
				Node33 receiver,
				Type22.Feature feature,
				Node33 argument) {
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

	interface StaticValue extends Node33 {
		static Node33.StaticValue of(int production, Object value, Type22 type) {
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

	interface TypeMismatch extends Node33 {
		static TypeMismatch of(int production, Type22 expected) {
			return new TypeMismatch() {
				@Override
				public String toString() {
					return "(!:" + expected + ")";
				}
			};
		}
	}

	interface ConstructProduct extends Node33 {
		static Node33.ConstructProduct of(int production, Type22 type, Node33... components) {
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

	interface LetBindingReference extends Node33 {
		static LetBindingReference of(Node33 referenced) {
			return new LetBindingReference() {

			};
		}
	}

	interface StructuralMismatch extends Node33 {
		static StructuralMismatch of(int production, Type22 expected) {
			return new StructuralMismatch() {
				@Override
				public String toString() {
					return " != " + expected;
				}
			};
		}
	}

	interface ApplyConstructor extends Node33 {

	}

	interface ConstructRecord extends Node33 {

	}
}
