package io.immutables.lang.type;

import io.immutables.collect.Vect;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TypeSignature implements Named, Parameterizable {
	private final String name;
	private final Vect<Type.Parameter> parameters;
	private final TypeConstructor constructor;

	private TypeSignature(String name, Vect<Type.Parameter> parameters) {
		this.name = name;
		this.parameters = parameters;
		this.constructor = parameters.isEmpty()
				? Type.Terminal.define(name)
				: parameterizedTyctor();
	}

	private TypeConstructor parameterizedTyctor() {
		return new TypeConstructor() {
			@Override public String name() {
				return name;
			}

			@Override public Vect<Type.Parameter> parameters() {
				return parameters;
			}

			@Override public Type.Nominal instantiate(Vect<Type> arguments) {
				return Type.Applied.instance(this, arguments);
			}

			@Override public String toString() {
				return name + parameters.join(", ", "<", ">");
			}
		};
	}

	@Override public String name() {
		return name;
	}

	@Override public Vect<Type.Parameter> parameters() {
		return parameters;
	}

	public TypeConstructor constructor() {
		return constructor;
	}

	public static Composer compose(String name) {
		return new Composer(name);
	}

	@Override public String toString() {
		return constructor.toString();
	}

	public static TypeSignature name(String name, String... parameters) {
		var c = new Composer(name);
		for (var p : parameters) c.parameter(p);
		return c.create();
	}

	public static final class Composer {
		private final String name;
		private final Map<String, Type.Parameter> parameters = new LinkedHashMap<>();

		private Composer(String name) {
			this.name = name;
		}

		public Type.Parameter parameter(String name) {
			var parameter = Type.Parameter.introduce(name, parameters.size());
			var previous = parameters.put(name, parameter);
			if (previous != null) throw new IllegalStateException("duplicate parameter in signature " + name);
			return parameter;
		}

		public TypeSignature create() {
			return new TypeSignature(name, Vect.from(parameters.values()));
		}
	}
}
