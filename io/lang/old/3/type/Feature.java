package io.immutables.lang.type;

import io.immutables.Nullable;
import io.immutables.collect.Vect;

interface Feature extends Named {
	Type in();
	Type out();

	Vect<Type.Variable> variables();

	interface Resolver {
		@Nullable Feature get(Type on, String name);
	}
}
