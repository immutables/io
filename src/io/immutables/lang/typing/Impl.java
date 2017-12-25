package io.immutables.lang.typing;

import com.google.common.base.Joiner;
import io.immutables.grammar.Symbol;
import io.immutables.lang.typing.Type.Declared;

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
				return v.unresolved(this, in);
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

	static Type.Declared declared(Symbol name, Type[] arguments, Type constructorIn) {
		return new Type.Declared() {
			final Type.Constructor[] constructors =
					constructorIn != Type.Undefined
							? new Type.Constructor[] {constructor(this, constructorIn)}
							: NO_CONSTRUCTORS;

			@Override
			public Symbol name() {
				return name;
			}

			@Override
			public Type[] arguments() {
				return arguments;
			}

			@Override
			public Constructor[] constructors() {
				return constructors;
			}

			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return v.declared(this, in);
			}

			@Override
			public String toString() {
				return name + (arguments.length > 0 ? "<" + Joiner.on(", ").join(arguments) + ">" : "");
			}

			@Override
			public Type withArguments(Type[] arguments) {
				return declared(name, arguments, constructorIn);
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

	static Type.Converted converted(Type.Arrow arrow) {
		return new Type.Converted() {
			@Override
			public Arrow arrow() {
				return arrow;
			}
			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return v.converted(this, in);
			}
			@Override
			public String toString() {
				return "::" + arrow;
			}
		};
	}

	static Type.Constructor constructor(Type.Declared out, Type in) {
		return new Type.Constructor() {

			@Override
			public Symbol name() {
				return unnamed();
			}

			@Override
			public Type in() {
				return in;
			}

			@Override
			public Declared out() {
				return out;
			}

			@Override
			public String toString() {
				return "" + out + (in == Empty ? "" : in instanceof Type.Product ? in : ("(" + in + ")"));
			}
		};
	}

	private static final Type.Constructor[] NO_CONSTRUCTORS = new Type.Constructor[0];
	private static final Type.Feature[] NO_FEATURES = new Type.Feature[0];
	private static final Type.Parameter[] NO_PARAMETERS = new Type.Parameter[0];

	static Symbol unnamed() {
		return Symbol.from("");
	}
}
