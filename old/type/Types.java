package io.immutables.lang.type;

import io.immutables.collect.Vect;
import io.immutables.lang.type.Type.Declared;
import io.immutables.lang.type.Type.Empty;
import io.immutables.lang.type.Type.Feature;
import io.immutables.lang.type.Type.Parameter;
import io.immutables.lang.type.Type.Product;
import io.immutables.lang.type.Type.Symbol;
import io.immutables.lang.type.Type.Undefined;
import io.immutables.lang.type.Type.Variable;

final class Types {
	private Types() {}

	static final Undefined Undefined = undefined();

	private static Undefined undefined() {
		return new Undefined() {
			@Override
			public String toString() {
				return Show.toString(this);
			}
			@Override
			public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.undefined(this, in);
			}
		};
	}

	static Parameter parameter(int index, Name name) {
		assert index >= 0;
		return new Parameter() {
			@Override
			public Name name() {
				return name;
			}
			@Override
			public int index() {
				return index;
			}
			@Override
			public String toString() {
				return Show.toString(this);
			}
			@Override
			public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.parameter(this, in);
			}
		};
	}

	static Variable variable(Name name) {
		return new Variable() {
			@Override
			public Name name() {
				return name;
			}
			@Override
			public String toString() {
				return Show.toString(this);
			}
			@Override
			public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.variable(this, in);
			}
		};
	}

	static Empty empty() {
		return new Empty() {
			@Override
			public String toString() {
				return Show.toString(this);
			}
			@Override
			public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.empty(this, in);
			}
		};
	}

	static Product product(Vect<Type> components) {
		return new Product() {
			@Override
			public Vect<Type> components() {
				return components;
			}
			@Override
			public String toString() {
				return Show.toString(this);
			}
			@Override
			public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.product(this, in);
			}
		};
	}

	static Symbol symbol(Name name, Name... parameters) {
		Vect<Parameter> ps = toParameters(parameters);
		return new Symbol() {
			@Override
			public Vect<Parameter> parameters() {
				return ps;
			}
			@Override
			public Name name() {
				return name;
			}
			@Override
			public String toString() {
				return Show.toString(this);
			}
		};
	}

	static Declared declared(Symbol symbol, Vect<Type> arguments) {
		assert symbol.parameters().size() == arguments.size();
		return new Declared() {
			@Override
			public Name name() {
				return symbol.name();
			}
			@Override
			public Vect<Type> arguments() {
				return arguments;
			}
			@Override
			public String toString() {
				return Show.toString(this);
			}
			@Override
			public <I, O> O accept(Visitor<I, O> v, I in) {
				return v.declared(this, in);
			}
		};
	}

	static Feature feature(Symbol symbol, Type in, Type out) {
		return new Feature() {
			@Override
			public Vect<Parameter> parameters() {
				return symbol.parameters();
			}
			@Override
			public Name name() {
				return symbol.name();
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
				return Show.toString(this);
			}
		};
	}

	private static Vect<Parameter> toParameters(Name... names) {
		Vect.Builder<Parameter> p = Vect.builder();
		for (int i = 0; i < names.length; i++) {
			p.add(parameter(i, names[i]));
		}
		return p.build();
	}

	public static void main(String... args) {
		System.out.println(Undefined);
		System.out.println(parameter(0, Name.of("T")));
		System.out.println(parameter(1, Name.of("X")));
		System.out.println(variable(Name.of("A")));
		System.out.println(empty());
		System.out.println(product(Vect.of(
				variable(Name.of("A")),
				variable(Name.of("B")))));
		System.out.println(symbol(Name.of("Aaa")));
		Symbol Bbc = symbol(Name.of("Bbb"), Name.of("T"), Name.of("Z"));
		System.out.println(Bbc);
		System.out.println(declared(Bbc, Vect.of(
				variable(Name.of("A")),
				variable(Name.of("A")))));

		Symbol ft = symbol(Name.of("_"), Name.of("T"), Name.of("U"));
		Parameter T = ft.parameters().get(0);
		Parameter U = ft.parameters().get(1);

		System.out.println(feature(ft, product(Vect.of(T, U)), T));
		System.out.println(feature(ft, empty(), empty()));

		Symbol abc = symbol(Name.of("abc"));
		System.out.println(feature(abc, empty(), Undefined));
	}
}
