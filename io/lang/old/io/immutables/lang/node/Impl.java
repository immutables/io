package io.immutables.lang.node;

import com.google.common.base.Joiner;
import io.immutables.collect.Vect;
import io.immutables.lang.node.Type.Feature;

final class Impl {
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

	static Type.Feature getFeature(Vect<Type.Feature> features, Name name) {
		for (Type.Feature f : features) {
			if (f.name().equals(name)) {
				return f;
			}
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

	static Type.Unresolved unresolved(Name name) {
		return new Type.Unresolved() {
			@Override
			public Name name() {
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

	static Type.Feature feature(Name name, Type in, Type out) {
		return new Type.Feature() {
			@Override
			public Name name() {
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

	static Type.Feature missing(Name name) {
		return new Type.Feature() {
			@Override
			public Name name() {
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

	static Type.Variable allocate(Name name) {
		return new Type.Variable() {
			@Override
			public Name name() {
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

//	@Immutable
//	public static abstract class DefinedImpl implements Type.Declared {
//		private final int hashCode = System.identityHashCode(this);
//
//		@Override
//		public abstract Vect<Feature> features();
//
//		@Override
//		public boolean equals(Object obj) {
//			return this == obj;
//		}
//
//		@Override
//		public String toString() {
//			return name() + (!arguments().isEmpty() ? "<" + Joiner.on(", ").join(arguments()) + ">" : "");
//		}
//
//		@Override
//		public int hashCode() {
//			return hashCode;
//		}
//
//		public static final class Builder extends ImmutableDefinedImpl.Builder {}
//	}

	static Type.Declared declared(Name name, Type... arguments) {
		return declared(name, arguments, Undefined, Vect.of());
	}

	static Type.Declared declared(Name name, Vect<Feature> features, Type... arguments) {
		return declared(name, arguments, Undefined, features);
	}

	static Type.Declared simple(Name name, Vect<Feature> features) {
		return new Type.Declared() {
			@Override
			public Name name() {
				return name;
			}

			@Override
			public Vect<Feature> features() {
				return features;
			}

			@Override
			public <I, O> O accept(Type.Visitor<I, O> v, I in) {
				return v.declared(this, in);
			}

			@Override
			public String toString() {
				return name.toString();
			}
		};
	}

	static Type.Declared withArguments(Name name, Vect<Feature> features, Type... arguments) {
		assert arguments.length > 0;
		return new DeclaredImplementation(arguments, name, features);
	}

	private static final class DeclaredImplementation implements Type.Declared {
		private final Type[] arguments;
		private final Name name;
		private final Vect<Feature> features;
		private DeclaredImplementation(Type[] arguments, Name name, Vect<Feature> features) {
			this.arguments = arguments;
			this.name = name;
			this.features = features;
		}
		@Override
		public Name name() {
			return name;
		}
		@Override
		public Vect<Feature> features() {
			return features;
		}
		@Override
		public <I, O> O accept(Type.Visitor<I, O> v, I in) {
			return v.declared(this, in);
		}
		@Override
		public String toString() {
			return name + "<" + Joiner.on(", ").join(arguments) + ">";
		}
		@Override
		public boolean equals(Object obj) {
			return obj instanceof  eq();
		}
	}

	static Type.Parameter declare(int index, Name name) {
		return new Type.Parameter() {
			@Override
			public Name name() {
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
