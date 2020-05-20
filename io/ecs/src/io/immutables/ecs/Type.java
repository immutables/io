package io.immutables.ecs;

import io.immutables.collect.Vect;
import org.immutables.data.Data;
import org.immutables.trees.Trees.Transform;
import org.immutables.trees.Trees.Visit;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Data
@Visit
@Transform
@Enclosing
public abstract class Type {
	Type() {}

	@Immutable
	public static abstract class Var {
		@Parameter
		public abstract String name();

		@Override
		public String toString() {
			return name();
		}

		public static Var of(String name) {
			return ImmutableType.Var.of(name);
		}

		public static final class Builder extends ImmutableType.Parameterized.Builder {}
	}

	@Immutable
	public static abstract class Reference {
		@Parameter
		public abstract String module();

		@Parameter
		public abstract String name();

		@Override
		public String toString() {
			return module() + "." + name();
		}

		public static final class Builder extends ImmutableType.Parameterized.Builder {}

		public static Reference of(String module, String name) {
			return ImmutableType.Reference.of(module, name);
		}
	}

	@Immutable
	public static abstract class Parameterized {
		@Parameter
		public abstract Reference reference();

		@Parameter
		public abstract Vect<Type> arguments();

		@Override
		public String toString() {
			return reference() + arguments().join(", ", "<", ">");
		}

		public static final class Builder extends ImmutableType.Parameterized.Builder {}

		public static Parameterized of(Reference reference, Vect<Type> arguments) {
			return ImmutableType.Parameterized.of(reference, arguments);
		}
	}

	public static abstract class Visitor extends TypeVisitor {}
	public static abstract class Transformer extends TypeVisitor {}
}
