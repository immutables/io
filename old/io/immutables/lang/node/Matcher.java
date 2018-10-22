package io.immutables.lang.node;

import io.immutables.Nullable;
import java.util.Arrays;

/**
 * Performs strict type checking / unification, with no auto-instantiations, conversions etc.
 */
class Matcher implements Type.Visitor<Type, Type> {
	protected final Type.Parameter[] parameters;
	protected final Type[] arguments;

	public Matcher() {
		this(Impl.noParameters());
	}

	public Matcher(Type.Parameter... parameters) {
		this(parameters, undefinedArguments(parameters.length));
	}

	protected Matcher(Type.Parameter[] parameters, Type[] arguments) {
		this.parameters = parameters;
		this.arguments = arguments;
	}

	static Matcher havingParameters(Name... names) {
		Type.Parameter[] parameters = new Type.Parameter[names.length];
		for (int i = 0; i < names.length; i++) {
			parameters[i] = Type.Parameter.declare(i, names[i]);
		}
		return new Matcher(parameters);
	}

	public Matcher matcher() {
		return new Matcher(parameters, arguments.clone());
	}

	protected void copyArguments(Matcher m) {
		assert parameters == m.parameters;
		assert arguments.length == m.arguments.length;
		System.arraycopy(m.arguments, 0, arguments, 0, m.arguments.length);
	}

	public boolean match(Type expect, Type actual) {
		return expect.accept(this, actual) != Type.Undefined;
	}

	public Type get(Type.Parameter p) {
		assert parameters[p.index()] == p;
		return arguments[p.index()];
	}

	@Override
	public Type empty(Type actual) {
		return actual == Type.Empty ? Type.Empty : Type.Undefined;
	}

	@Override
	public Type product(Type.Product expected, Type actual) {
		return actual.accept(new Type.Visitor<Type.Product, Type>() {
			@Override
			public Type product(Type.Product actual, Type.Product expected) {
				Type[] actualComponents = actual.components();
				@Nullable Type[] matched = matchComponentwise(expected.components(), actualComponents);
				if (matched != null) {
					return actualComponents != matched ? Type.Product.of(matched) : actual;
				}
				// don't call productOtherwise, alternative match is not allowed
				return Type.Undefined;
			}
			@Override
			public Type otherwise(Type actual, Type.Product expected) {
				return productOtherwise(expected, actual);
			}
		}, expected);
	}

	@Override
	public Type declared(Type.Declared expected, Type actual) {
		return actual.accept(new Type.Visitor<Type.Declared, Type>() {
			@Override
			public Type declared(Type.Declared actual, Type.Declared expected) {
				if (expected.sameDeclaration(actual)) {
					Type[] actualArguments = actual.arguments();
					@Nullable Type[] matched = matchComponentwise(expected.arguments(), actualArguments);
					if (matched != null) {
						return actualArguments != matched ? actual.withArguments(matched) : actual;
					}
					// don't call declaredOtherwise, alternative match is not allowed
					// if declared are of the same type constructor
					return Type.Undefined;
				}
				return declaredOtherwise(expected, actual);
			}
			@Override
			public Type otherwise(Type actual, Type.Declared expected) {
				return declaredOtherwise(expected, actual);
			}
		}, expected);
	}

	@SuppressWarnings("unused")
	protected Type declaredOtherwise(Type.Declared expected, Type actual) {
		return Type.Undefined;
	}

	@SuppressWarnings("unused")
	protected Type productOtherwise(Type.Product expected, Type actual) {
		return Type.Undefined;
	}

	@Override
	public Type otherwise(Type expected, Type actual) {
		return expected == actual ? actual : Type.Undefined;
	}

	@Override
	public Type parameter(Type.Parameter expected, Type actual) {
		int i = expected.index();
		assert parameters[i] == expected : "Undeclared parameter " + expected;
		if (arguments[i] != Type.Undefined && !match(arguments[i], actual)) {
			arguments[i] = new Conflict(arguments[i], actual);
			return Type.Undefined;
		}
		return arguments[i] = actual;
	}

	protected @Nullable Type[] matchComponentwise(Type[] expect, Type[] actual) {
		if (expect.length != actual.length) return null;
		Type[] result = null; // may be needed if we're copying
		for (int i = 0; i < expect.length; i++) {
			Type a = actual[i];
			Type r = expect[i].accept(this, a);
			if (r == Type.Undefined) return null;
			if (result != null) {
				// already copying to result array
				result[i] = r;
			} else if (r != a) {
				// start copy to a new result array if type differ
				result = new Type[expect.length];
				System.arraycopy(actual, 0, result, 0, i);
				result[i] = r;
			} // just stay on actual array
		}
		return result != null ? result : actual;
	}

	private static final class Conflict implements Type {
		private final Type left;
		private final Type right;
		Conflict(Type left, Type right) {
			this.left = left;
			this.right = right;
		}
		@Override
		public String toString() {
			return left + " =/= " + right;
		}
	}

	private static Type[] undefinedArguments(int length) {
		if (length == 0) return Impl.noTypes();
		Type[] args = new Type[length];
		Arrays.fill(args, Type.Undefined);
		return args;
	}
}
