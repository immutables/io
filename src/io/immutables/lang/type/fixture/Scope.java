package io.immutables.lang.type.fixture;

import io.immutables.collect.Vect;
import io.immutables.lang.type.Name;
import io.immutables.lang.type.Type;
import io.immutables.lang.type.Type.Feature;
import javax.annotation.Nullable;

final class Scope implements Node.Scoped, Type {
	private static Scope INIT = new Scope(null, null, null);
	private final @Nullable Scope parent;
	private final @Nullable Name name;
	private final @Nullable Node value;

	private Scope(Scope parent, Name name, Node value) {
		this.parent = parent;
		this.name = name;
		this.value = value;
	}

	@Override
	public Vect<Feature> features() {
		Vect<Feature> fs = Vect.of();
		for (Scope s = this; s != null; s = s.parent) {
			fs = fs.prepend(s.toAccessor());
		}
		return fs;
	}

	private Feature toAccessor() {
		return Feature.simple(name, Type.Empty, value.type());
	}

	@Override
	public Feature getFeature(Name name) {
		for (Scope s = this; s != null; s = s.parent) {
			if (s.name.equals(name)) {
				return s.toAccessor();
			}
		}
		return Feature.missing(name);
	}

	static Scope init() {
		return INIT;
	}

	Scope let(Name name, Node value) {
		return new Scope(this, name, value);
	}

	@Nullable
	Node get(Name name) {
		if (this.name == null) return null;
		if (this.name.equals(name)) return value;
		assert parent != null;
		return this.parent.get(name);
	}

	@Override
	public String toString() {
		return "<scope>";
	}
}