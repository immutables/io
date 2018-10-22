package io.immutables.lang.type22;

import com.google.common.base.Joiner;
import io.immutables.collect.Vect;
import io.immutables.lang.type22.ImmutableDefinedImpl;
import io.immutables.lang.type22.Type22.Declared;
import org.immutables.value.Value.Immutable;

@Immutable
public abstract class DefinedImpl implements Declared {
	private final int hashCode = System.identityHashCode(this);

	@Override
	public DefinedImpl applyArguments(Vect<Type22> arguments) {
		throw new UnsupportedOperationException();
	}

	@Override
	public abstract Vect<Feature> features();

	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}

	@Override
	public String toString() {
		return name() + (!arguments().isEmpty() ? "<" + Joiner.on(", ").join(arguments()) + ">" : "");
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	public static final class Builder extends ImmutableDefinedImpl.Builder {}
}
