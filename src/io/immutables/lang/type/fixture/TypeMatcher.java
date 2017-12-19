package io.immutables.lang.type.fixture;

import io.immutables.collect.Vect;
import io.immutables.lang.type.Type;
import io.immutables.lang.type.Type.Declared;
import io.immutables.lang.type.Type.Product;
import io.immutables.lang.type.Type.Variable;
import io.immutables.lang.type.Type.Visitor;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

class TypeMatcher implements Type.Visitor<Type, Boolean> {
	final Map<Type.Variable, Type> arguments = new IdentityHashMap<>(8);
	TypeMatcher(Type.Variable... parameters) {
		this(Vect.of(parameters));
	}
	TypeMatcher(Vect<Type.Variable> parameters) {
		for (Type.Variable p : parameters) {
			this.arguments.put(p, null);
		}
	}
	boolean satisfied() {
		return arguments.values().stream().allMatch(Objects::nonNull);
	}
	boolean match(Type expect, Type actual) {
		return expect.accept(this, actual);
	}
	@Override
	public Boolean otherwise(Type t, Type in) {
		return t.eq(in);
	}
	@Override
	public Boolean variable(Variable v, Type in) {
		if (arguments.containsKey(v)) {
			Type existing = arguments.put(v, in);
			// TODO add mismatch
			return existing == null || existing.eq(in);
		}
		return otherwise(v, in);
	}
	@Override
	public Boolean declared(Declared expect, Type in) {
		return new MatchOnly() {
			@Override
			public Boolean declared(Declared actual, Void in) {
				return expect.sameDeclaration(actual)
						&& matchComponentwise(
								expect.arguments(),
								actual.arguments());
			}
		}.matches(in);
	}
	@Override
	public Boolean product(Product expect, Type in) {
		return new MatchOnly() {
			@Override
			public Boolean product(Product actual, Void in) {
				return matchComponentwise(
						expect.components(),
						actual.components());
			}
		}.matches(in);
	}
	protected boolean matchComponentwise(Vect<Type> expect, Vect<Type> actual) {
		int size = expect.size();
		if (size != actual.size()) return false;
		for (int i = 0; i < size; i++) {
			if (!match(expect.get(i), actual.get(i))) {
				return false;
			}
		}
		return true;
	}
	class MatchOnly implements Visitor<Void, Boolean> {
		@Override
		public Boolean otherwise(Type t, Void in) {
			return false;
		}
		Boolean matches(Type t) {
			return t.accept(this, null);
		}
	}
}
