package io.immutables.lang.type22.fixture;

import io.immutables.collect.Vect;
import io.immutables.lang.type22.Type22;
import io.immutables.lang.type22.Type22.Declared;
import io.immutables.lang.type22.Type22.Product;
import io.immutables.lang.type22.Type22.Variable;
import io.immutables.lang.type22.Type22.Visitor;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

class TypeMatcher implements Type22.Visitor<Type22, Boolean> {
	final Map<Type22.Variable, Type22> arguments = new IdentityHashMap<>(8);
	TypeMatcher(Type22.Variable... parameters) {
		this(Vect.of(parameters));
	}
	TypeMatcher(Vect<Type22.Variable> parameters) {
		for (Type22.Variable p : parameters) {
			this.arguments.put(p, null);
		}
	}
	boolean satisfied() {
		return arguments.values().stream().allMatch(Objects::nonNull);
	}
	boolean match(Type22 expect, Type22 actual) {
		return expect.accept(this, actual);
	}
	@Override
	public Boolean otherwise(Type22 t, Type22 in) {
		return t.eq(in);
	}
	@Override
	public Boolean variable(Variable v, Type22 in) {
		if (arguments.containsKey(v)) {
			Type22 existing = arguments.put(v, in);
			// TODO add mismatch
			return existing == null || existing.eq(in);
		}
		return otherwise(v, in);
	}
	@Override
	public Boolean declared(Declared expect, Type22 in) {
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
	public Boolean product(Product expect, Type22 in) {
		return new MatchOnly() {
			@Override
			public Boolean product(Product actual, Void in) {
				return matchComponentwise(
						expect.components(),
						actual.components());
			}
		}.matches(in);
	}
	protected boolean matchComponentwise(Vect<Type22> expect, Vect<Type22> actual) {
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
		public Boolean otherwise(Type22 t, Void in) {
			return false;
		}
		Boolean matches(Type22 t) {
			return t.accept(this, null);
		}
	}
}
