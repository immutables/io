package io.immutables.lang.node;

import com.google.common.base.Joiner;

interface Node {
	boolean checks();

	default Type type() {
		return Type.Undefined;
	}

	Node Empty = new Node() {
		@Override
		public Type type() {
			return Type.Empty;
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
		public Type type() {
			return Type.Undefined;
		}

		@Override
		public String toString() {
			return "<NA>";
		}

		@Override
		public boolean checks() {
			return false;
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
					return receiver + "." + feature + " <~/~ " + argument;
				}

				@Override
				public boolean checks() {
					return false;
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
					return feature.out();
				}
				@Override
				public boolean checks() {
					return true;
				}
				@Override
				public String toString() {
					return receiver + "." + feature.name()
							+ (feature.in() == Type.Empty ? "" : ("(" + argument + ")")) + ": " + type();
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
		static TypeMismatch of(int production, Type expected) {
			return new TypeMismatch() {
				@Override
				public String toString() {
					return "(!:" + expected + ")";
				}

				@Override
				public boolean checks() {
					return false;
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
}
