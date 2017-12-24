package io.immutables.lang.typing;

import java.util.Arrays;

interface Checking {
	class Coercer extends Matcher {
		private boolean coercion;
		public Coercer() {}

		public Coercer(Type.Parameter... parameters) {
			super(parameters);
		}

		protected Coercer(Type.Parameter[] parameters, Type[] arguments) {
			super(parameters, arguments);
		}

		@Override
		public Coercer clone() {
			return new Coercer(parameters, arguments.clone());
		}

		@Override
		protected boolean matchToNominal(Type.Nominal expected, Type.Nominal actual) {
			return super.matchToNominal(expected, actual);
		}

		@Override
		protected boolean matchToNominal(Type.Nominal expected, Type actual) {
			return super.matchToNominal(expected, actual);
		}

		@Override
		protected boolean matchToProduct(Type.Product expected, Type actual) {
			return super.matchToProduct(expected, actual);
		}
	}

	class Matcher implements Type.Visitor<Type, Boolean> {
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

		@Override
		public Matcher clone() {
			return new Matcher(parameters, arguments.clone());
		}

		public boolean match(Type expect, Type actual) {
			return expect.accept(this, actual);
		}

		public Type get(Type.Parameter p) {
			assert parameters[p.index()] == p;
			return arguments[p.index()];
		}

		@Override
		public Boolean empty(Type actual) {
			return actual == Type.Empty;
		}

		@Override
		public Boolean product(Type.Product expected, Type actual) {
			return actual.accept(new Type.Visitor<Type.Product, Boolean>() {
				@Override
				public Boolean product(Type.Product actual, Type.Product expected) {
					return matchComponentwise(expected.components(), actual.components());
				}
				@Override
				public Boolean otherwise(Type actual, Type.Product expected) {
					return matchToProduct(expected, actual);
				}
			}, expected);
		}

		@Override
		public Boolean nominal(Type.Nominal expected, Type actual) {
			return actual.accept(new Type.Visitor<Type.Nominal, Boolean>() {
				@Override
				public Boolean nominal(Type.Nominal actual, Type.Nominal expected) {
					if (expected.name().equals(actual.name())) {
						return matchComponentwise(expected.arguments(), actual.arguments());
					}
					return matchToNominal(expected, actual);
				}
				@Override
				public Boolean otherwise(Type actual, Type.Nominal expected) {
					return matchToNominal(expected, actual);
				}
			}, expected);
		}

		@SuppressWarnings("unused")
		protected boolean matchToNominal(Type.Nominal expected, Type.Nominal actual) {
			return false;
		}

		@SuppressWarnings("unused")
		protected boolean matchToNominal(Type.Nominal expected, Type actual) {
			return false;
		}

		@SuppressWarnings("unused")
		protected boolean matchToProduct(Type.Product expected, Type actual) {
			return false;
		}

		@Override
		public Boolean otherwise(Type expected, Type actual) {
			return expected != actual;
		}

		@Override
		public Boolean parameter(Type.Parameter expected, Type actual) {
			int i = expected.index();
			assert parameters[i] == expected;
			if (arguments[i] != Type.Undefined && !match(arguments[i], actual)) {
				arguments[i] = new Conflict(arguments[i], actual);
				return false;
			}
			arguments[i] = actual;
			return true;
		}

		protected boolean matchComponentwise(Type[] expect, Type[] actual) {
			if (expect.length != actual.length) return false;
			for (int i = 0; i < expect.length; i++) {
				if (!match(expect[i], actual[i])) return false;
			}
			return true;
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
}
