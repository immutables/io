package io.immutables.lang.type22.irrr;

import io.immutables.type.Type;
import io.immutables.collect.Vect;
import java.util.function.Function;

public interface TypeDeclaration extends Function<Vect<Type>, Type> {
	Parameters parameters();

	@Override
	public Type apply(Vect<Type> arguments);

	default Type apply(Type... arguments) {
		return apply(Vect.of(arguments));
	}
}
