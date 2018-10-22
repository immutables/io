package io.immutables.lang.node;

import org.immutables.value.Value.Default;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Immutable(singleton = true, builder = false, copy = false)
public abstract class Name {
	@Parameter
	@Default
	String value() {
		return "";
	}
	public static Name empty() {
		return ImmutableName.of();
	}
	public static Name of(String value) {
		return ImmutableName.of(value);
	}
	public boolean isEmpty() {
		return value().isEmpty();
	}
	@Override
	public String toString() {
		return value();
	}
}
