package io.immutables.lang.type22.irrr;

import io.immutables.type.Type;
import com.google.common.base.Joiner;
import io.immutables.collect.Vect;
import io.immutables.type.Type.Variable;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.annotation.Nullable;

interface Parameters {
	Parameters introduce(CharSequence name);
	Variable get();
	Optional<Variable> find(CharSequence name);
	Vect<Variable> unwind();
	long length();

	default boolean isEmpty() {
		return length() == 0;
	}

	static Parameters empty() {
		return EmptyParameters.EMPTY;
	}
}

final class EmptyParameters implements Parameters {
	static final Parameters EMPTY = new EmptyParameters();

	@Override
	public Parameters introduce(CharSequence name) {
		return new Cons(null, name);
	}

	@Override
	public Optional<Variable> find(CharSequence name) {
		return Optional.empty();
	}

	@Override
	public Vect<Variable> unwind() {
		return Vect.of();
	}

	@Override
	public Variable get() {
		throw new NoSuchElementException("empty parameter list do not have a head variable");
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public long length() {
		return 0;
	}

	@Override
	public String toString() {
		return "<>";
	}

	private static final class Cons implements Parameters {
		private final @Nullable Cons parent;
		private final Variable variable;
		private final int length;

		private Cons(@Nullable Cons parent, CharSequence name) {
			this.parent = parent;
			this.variable = new Var(name);
			this.length = parent != null ? parent.length + 1 : 1;
		}

		@Override
		public Parameters introduce(CharSequence name) {
			return new Cons(this, name);
		}

		@Override
		public Optional<Variable> find(CharSequence name) {
			for (@Nullable Cons c = this; c != null; c = c.parent) {
				if (c.variable.name().equals(name)) {
					return Optional.of(c.variable);
				}
			}
			return Optional.empty();
		}

		@Override
		public Vect<Variable> unwind() {
			Vect.Builder<Variable> b = Vect.builder();
			for (@Nullable Cons c = this; c != null; c = c.parent) {
				b.add(c.variable);
			}
			return b.build().reverse();
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public Variable get() {
			return variable;
		}

		@Override
		public String toString() {
			return "<" + Joiner.on(",").join(unwind()) + ">";
		}

		@Override
		public long length() {
			return length;
		}
	}

	private static final class Var implements Variable {
		private final CharSequence name;
		private final int hash;

		private Var(CharSequence name) {
			this.name = name;
			this.hash = name.hashCode();
		}

		@Override
		public CharSequence name() {
			return name;
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}

		@Override
		public String toString() {
			return name.toString();
		}
	}
}
