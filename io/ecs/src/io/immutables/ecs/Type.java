package io.immutables.ecs;

import io.immutables.collect.Vect;
import org.immutables.data.Data;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Derived;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Data
@Enclosing
public interface Type {
	<I, O> O accept(Visitor<I, O> v, I in);

	interface Named {
		String name();
	}

	@Immutable
	abstract class Variable implements Type, Named {
		@Override
		public abstract @Parameter String name();

		@Override
		public String toString() {
			return name();
		}

		public static Variable of(String name) {
			return ImmutableType.Variable.of(name);
		}

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.variable(this, in);
		}
	}

	interface Structural extends Type {}

	@Immutable
	abstract class Unresolved implements Type, Named {
		@Override public abstract @Parameter String name();

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.unresolved(in, this);
		}

		@Override
		public String toString() {
			return "!" + name() + "!";
		}

		public static Unresolved of(String name) {
			return ImmutableType.Unresolved.of(name);
		}
	}

	@Immutable
	abstract class Reference implements Type, Named {
		public abstract @Parameter String module();
		@Override public abstract @Parameter String name();

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.reference(this, in);
		}

		public static Reference of(String module, String name) {
			return ImmutableType.Reference.of(module, name);
		}

		@Override
		public String toString() {
			return module() + ":" + name();
		}

		public static final class Builder extends ImmutableType.Reference.Builder {}
	}

	@Immutable
	abstract class Option implements Structural {
		public abstract @Parameter Type component();

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.option(this, in);
		}

		@Override
		public String toString() {
			return component() + "?";
		}

		public static Option of(Type component) {
			return ImmutableType.Option.of(component);
		}
	}

	@Immutable
	abstract class Parameterized implements Type {
		public abstract @Parameter Reference reference();
		public abstract @Parameter Vect<Type> arguments();

		@Check void hasArguments() {
			checkState(!arguments().isEmpty());
		}

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.parameterized(this, in);
		}

		@Override
		public String toString() {
			return reference() + arguments().join(", ", "<", ">");
		}

		public static Parameterized of(Reference reference, Type... args) {
			return of(reference, Vect.of(args));
		}

		public static Parameterized of(Reference reference, Vect<Type> arguments) {
			return ImmutableType.Parameterized.of(reference, arguments);
		}

		public static final class Builder extends ImmutableType.Parameterized.Builder {}
	}

	@Immutable
	abstract class Alternative implements Structural {
		public abstract @Parameter Vect<Type> alternatives();

		public static Alternative of(Type... alternatives) {
			return of(Vect.of(alternatives));
		}

		public static Alternative of(Iterable<? extends Type> alternatives) {
			return ImmutableType.Alternative.of(alternatives);
		}

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.alternative(this, in);
		}

		@Override
		public String toString() {
			return alternatives().join(" | ");
		}
	}

	@Immutable(singleton = true)
	abstract class Empty implements Structural {
		public static Empty of() {
			return ImmutableType.Empty.of();
		}

		@Override
		public String toString() {
			return "()";
		}

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.empty(in);
		}
	}

	@Immutable
	abstract class Product implements Structural {
		public abstract @Parameter Vect<Type> components();

		@Override
		public String toString() {
			return components().join(", ", "(", ")");
		}

		public static Product of(Type... components) {
			checkArgument(components.length < 2);
			return of(Vect.of(components));
		}

		public static Product of(Iterable<? extends Type> components) {
			var c = Vect.from(components);
			checkArgument(c.size() < 2);
			return ImmutableType.Product.of(c);
		}

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.product(this, in);
		}
	}

	interface Parameterizable {
		Vect<Type.Variable> parameters();
	}

	@Immutable
	abstract class Feature implements Named, Parameterizable {
		@Override
		public abstract @Parameter String name();

		public abstract @Parameter Type in();

		public abstract @Parameter Type out();

		public @Derived boolean exists() { return true; }

		@Override
		public String toString() {
			var in = in().toString();
			if (in.isEmpty()
					|| !in.startsWith("(")
					|| !in.startsWith("{")
					|| !in.startsWith("[")) {
				in = "(" + in + ")";
			}
			return name() + in + " -> " + out();
		}

		public static Feature of(String name, Type in, Type out) {
			return ImmutableType.Feature.of(name, in, out);
		}

		public static Feature field(String name, Type out) {
			return of(name, Type.Empty.of(), out);
		}

		public static final class Builder extends ImmutableType.Feature.Builder {}

		public static Feature missing(String name) {
			return new Feature() {

				@Override public String name() { return name; }

				@Override public Type in() { return Type.Undefined; }

				@Override public Type out() { return Type.Undefined; }

				@Override public Vect<Variable> parameters() { return Vect.of(); }

				@Override public boolean exists() { return false; }

				@Override public String toString() {
					return "!" + name + "!" + in() + " -> " + out();
				}
			};
		}
	}

	@Immutable
	abstract class Record implements Structural {
		// TODO does it make sense to model fields not as features but dedicated named parameter objects
		public abstract @Parameter Vect<Feature> fields();

		@Check void onlyFieldFeatures() {
			checkState(fields()
					.all(f -> f.in() == Type.Empty.of() && f.parameters().isEmpty()));
		}

		@Override
		public String toString() {
			return fields().join(", ", "{", "}");
		}

		public static Record of(Iterable<? extends Feature> features) {
			return ImmutableType.Record.of(features);
		}

		public static final class Builder extends ImmutableType.Record.Builder {}

		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.record(this, in);
		}
	}

	interface Function extends Structural {
		Type in();
		Type out();

		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.functional(this, in);
		}
	}

	@Immutable
	interface Sequence extends Structural {
		// Sequence types are not thought out yet
		@Override
		default <I, O> O accept(Visitor<I, O> v, I in) {
			return v.sequence(this, in);
		}
	}

	Type Undefined = new Type() {
		@Override
		public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.undefined(in);
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return false;
		}

		@Override
		public String toString() {
			return "???";
		}
	};

	interface Visitor<I, O> {
		default O variable(Variable v, I in) {
			return otherwise(v, in);
		}

		default O reference(Reference d, I in) {
			return otherwise(d, in);
		}

		default O parameterized(Parameterized d, I in) {
			return otherwise(d, in);
		}

		default O product(Product p, I in) {
			return otherwise(p, in);
		}

		default O empty(I in) {
			return otherwise(Empty.of(), in);
		}

		default O record(Record r, I in) {
			return otherwise(r, in);
		}

		default O alternative(Alternative v, I in) {
			return otherwise(v, in);
		}

		default O sequence(Sequence s, I in) {
			return otherwise(s, in);
		}

		default O option(Option o, I in) {
			return otherwise(o, in);
		}

		default O functional(Function f, I in) {
			return otherwise(f, in);
		}

		default O unresolved(I in, Unresolved f) {
			return otherwise(f, in);
		}

		default O undefined(I in) {
			return otherwise(Undefined, in);
		}

		default O otherwise(Type t, I in) {
			throw new UnsupportedOperationException("cannot handle type " + t + " with input: " + in);
		}
	}
}
