package io.immutables.lang.type;

import io.immutables.collect.Vect;
import io.immutables.lang.type.Type.Declared;
import org.immutables.value.Value.Immutable;

@Immutable
abstract class DefinedImpl implements Declared {
	private final int hashCode = System.identityHashCode(this);

	@Override
	public DefinedImpl applyArguments(Vect<Type> arguments) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	static final class Builder extends ImmutableDefinedImpl.Builder {}
}
