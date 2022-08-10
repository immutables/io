package io.immutables.lang.type;

import io.immutables.Nullable;
import java.util.HashMap;
import java.util.Map;

class Scope {
	private final @Nullable Scope parent;
	private final Map<String, Type> references = new HashMap<>();

	private Scope(@Nullable Scope parent) {
		this.parent = parent;
	}

	Scope put(String name, Type type) {
		references.put(name, type);
		return this;
	}

	@Nullable Type typeFor(String name) {
		return references.get(name);
	}

	Scope sub() {
		return new Scope(this);
	}

	static Scope newTop() {
		return new Scope(null);
	}
}
