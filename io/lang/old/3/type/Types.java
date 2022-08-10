package io.immutables.lang.type;

import io.immutables.Nullable;
import io.immutables.collect.Vect;
import java.util.HashMap;
import java.util.function.Function;

final class Types {
	private Types() {}

	static final Type.Product Empty = new Type.Product() {
		@Override public Vect<Type> components() {
			return Vect.of();
		}

		@Override public boolean equals(Object o) {
			return this == o;
		}

		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.empty(in);
		}

		@Override public String toString() {
			return "()";
		}
	};

	static Type.Product product() {
		return Empty;
	}

	static Type.Product product(Type... components) {
		if (components.length == 0) return Empty;
		return product(Vect.of(components));
	}

	static Type.Product product(Vect<Type> components) {
		if (components.isEmpty()) return Empty;
		if (components.size() == 1) throw new AssertionError("Avoid 1-tuple");

		return new Type.Product() {
			@Override public Vect<Type> components() {
				return components;
			}

			@Override public boolean equals(Object o) {
				return o instanceof Type.Product
						&& ((Product) o).components().equals(components);
			}

			@Override public int hashCode() {
				return "PRODUCT/".hashCode() + components.hashCode();
			}

			@Override public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.product(this, in);
			}

			@Override public String toString() {
				return components.join(", ", "(", ")");
			}
		};
	}

	static Type.Variable allocate(String name) {
		return new Type.Variable() {
			@Override public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.variable(this, in);
			}

			@Override public String name() {
				return name;
			}

			@Override public String toString() {
				return "'" + name;
			}
		};
	}

	static Type.Basic create(String name) {
		return new Type.Basic() {
			@Override public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.basic(this, in);
			}

			@Override public Vect<Type.Variable> parameters() {
				return Vect.of();
			}

			@Override public Type construct(Vect<Type> arguments) {
				assert arguments.isEmpty() : "must have no arguments";
				return this;
			}

			@Override public String name() {
				return name;
			}

			@Override public String toString() {
				return name;
			}
		};
	}

	static Function<String, Type.Basic> basicFactory() {
		var basics = new HashMap<String, Type.Basic>();
		return name -> basics.computeIfAbsent(name, Types::create);
	}

	static Type.Tystructor typeConstructorId(String name, Type.Variable... parameters) {
		return typeConstructorId(name, Vect.of(parameters));
	}

	static Type.Tystructor typeConstructorId(String name, Vect<Type.Variable> parameters) {
		return new Type.Tystructor() {
			final @Nullable Type.Nominal instance =
					parameters.isEmpty() ? nominal(this, Vect.of()) : null;

			@Override public String name() {
				return name;
			}

			@Override public Vect<Type.Variable> parameters() {
				return parameters;
			}

			@Override public Type.Nominal construct(Vect<Type> arguments) {
				if (arguments.size() != parameters.size()) {
					// TODO constraint check from here ?
					throw new AssertionError(this + " <=!= " + arguments);
				}
				if (instance != null) return instance;
				return nominal(this, arguments);
			}

			@Override public String toString() {
				return name + (parameters.isEmpty() ? "" : parameters.join(" _, ", "<", " _>"));
			}
		};
	}

	private static Type.Nominal nominal(Type.Tystructor typeConstructor, Vect<Type> arguments) {
		return new Type.Nominal() {
			@Override public Tystructor tystructor() {
				return typeConstructor;
			}

			@Override public String name() {
				return typeConstructor.name();
			}

			@Override public Vect<Type> arguments() {
				return arguments;
			}

			@Override public boolean equals(Object o) {
				return o instanceof Type.Nominal
						&& ((Nominal) o).tystructor().equals(typeConstructor)
						&& ((Nominal) o).arguments().equals(arguments);
			}

			@Override public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.nominal(this, in);
			}

			@Override public int hashCode() {
				return "NOMINAL/".hashCode() * typeConstructor.hashCode() + arguments.hashCode();
			}

			@Override public String toString() {
				return typeConstructor.name() + (arguments.isEmpty() ? "" : arguments.join(", ", "<", ">"));
			}
		};
	}

	static final Type.Undefined Undefined = new Type.Undefined() {
		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.undefined(in);
		}

		@Override public String toString() {
			return "!?";
		}

		@Override public boolean equals(Object obj) {
			return false;
		}
	};
}
