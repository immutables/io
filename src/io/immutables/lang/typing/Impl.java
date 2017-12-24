package io.immutables.lang.typing;

import com.google.common.base.Joiner;
import io.immutables.grammar.Symbol;

public final class Impl {
	private Impl() {}

	static Type.Feature[] noFeatures() {
		return NO_FEATURES;
	}

	static Type.Parameter[] noParameters() {
		return NO_PARAMETERS;
	}

	static Type[] noTypes() {
		return NO_PARAMETERS; // subtype empty array is oks
	}

	static Type.Feature getFeature(Type.Feature[] features, Symbol name) {
		for (Type.Feature f : features) {
			if (f.name().equals(name)) return f;
		}
		return missing(name);
	}

	static final Type Undefined = new Type() {
		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.undefined(in);
		}

		@Override
		public String toString() {
			return "???";
		}
	};

	static final Type.Product Empty = product();

	static Type.Product product(Type... components) {
		assert components.length != 1 : "single component product is equivalent to the component itself";
		return new Type.Product() {
			@Override
			public Type[] components() {
				return components;
			}

			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return this == Empty
						? v.empty(in)
						: v.product(this, in);
			}

			@Override
			public String toString() {
				return "(" + Joiner.on(", ").join(components) + ")";
			}
		};
	}

	static Type.Unresolved unresolved(Symbol name) {
		return new Type.Unresolved() {
			@Override
			public Symbol name() {
				return name;
			}

			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return v.unresolved(in, this);
			}

			@Override
			public String toString() {
				return "!/" + name + "/!";
			}
		};
	}

	static Type.Feature feature(Symbol name, Type in, Type out) {
		return new Type.Feature() {
			@Override
			public Symbol name() {
				return name;
			}

			@Override
			public Type in() {
				return in;
			}

			@Override
			public Type out() {
				return out;
			}

			@Override
			public String toString() {
				return "" + name + (in == Empty ? "" : in instanceof Type.Product ? in : ("(" + in + ")")) + out;
			}

			@Override
			public Type.Parameter[] parameters() {
				return NO_PARAMETERS;
			}
		};
	}

	static Type.Feature missing(Symbol name) {
		return new Type.Feature() {
			@Override
			public Symbol name() {
				return name;
			}

			@Override
			public Type out() {
				return Undefined;
			}

			@Override
			public Type in() {
				return Undefined;
			}

			@Override
			public Type.Parameter[] parameters() {
				return NO_PARAMETERS;
			}

			@Override
			public String toString() {
				return name + "/??";
			}
		};
	}

	static Type.Variable allocate(Symbol name) {
		return new Type.Variable() {
			@Override
			public Symbol name() {
				return name;
			}

			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return v.variable(this, in);
			}

			@Override
			public String toString() {
				return "<" + name + ">";
			}
		};
	}

	static Type.Nominal nominal(Symbol name, Type[] arguments) {
		return new Type.Nominal() {
			@Override
			public Symbol name() {
				return name;
			}

			@Override
			public Type[] arguments() {
				return arguments;
			}

			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return v.nominal(this, in);
			}

			@Override
			public String toString() {
				return name + "<" + Joiner.on(", ").join(arguments) + ">";
			}
		};
	}

	static Type.Parameter declare(int index, Symbol name) {
		return new Type.Parameter() {
			@Override
			public Symbol name() {
				return name;
			}

			@Override
			public int index() {
				return index;
			}

			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return v.parameter(this, in);
			}

			@Override
			public String toString() {
				return "<" + index + ":" + name + ">";
			}
		};
	}

	private static final Type.Feature[] NO_FEATURES = new Type.Feature[0];
	private static final Type.Parameter[] NO_PARAMETERS = new Type.Parameter[0];
}
