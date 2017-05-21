package io.immutables.lang.type.irrr;

import io.immutables.type.Type;
import io.immutables.type.Type.Declared;
import io.immutables.type.Type.Variable;
import io.immutables.type.Type.Visitor;
import io.immutables.type.Type.Parameter;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ObjectArrays;
import java.util.function.Function;

public final class VariableSubstitution implements Type.Visitor<Type>, Function<Type, Type> {
	private static final VariableSubstitution EMPTY = new VariableSubstitution(new Type.Variable[0], new Type[0]);

	public static VariableSubstitution empty() {
		return EMPTY;
	}

	public boolean isEmpty() {
		return variables.length == 0;
	}

	private final Type.Variable[] variables;
	private final Type[] substitutions;

	private VariableSubstitution(Type.Variable[] variables, Type[] substitutions) {
		assert variables.length == substitutions.length;
		this.variables = variables;
		this.substitutions = substitutions;
	}

	@Override
	public Type variable(Type.Variable v) {
		for (int i = 0; i < variables.length; i++) {
			if (variables[i] == v) {
				return substitutions[i];
			}
		}
		return v;
	}

	@Override
	public Type declared(Type.Declared d) {
		return d.withArguments(d.arguments().map(this));
	}

	@Override
	public Type unsupported(Type t) {
		return t;
	}

	@Override
	public Type apply(Type t) {
		return t.accept(this);
	}

	@Override
	public String toString() {
		MoreObjects.ToStringHelper h = MoreObjects.toStringHelper(getClass());
		for (Type.Variable v : variables) {
			h.add(v.name().toString(), variable(v));
		}
		return h.toString();
	}

	public VariableSubstitution assign(Type.Variable variable, Type substitution) {
		return new VariableSubstitution(
				ObjectArrays.concat(variables, variable),
				ObjectArrays.concat(substitutions, substitution));
	}

	@Override
	public Type parameter(Parameter p) {
		throw new UnsupportedOperationException();
	}
}
