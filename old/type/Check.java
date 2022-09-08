package io.immutables.lang.type;

import io.immutables.lang.type.Type.Arrow;
import io.immutables.lang.type.Type.Empty;
import io.immutables.lang.type.Type.Typed;
import io.immutables.lang.type.Type.Visitor;

final class Check {
	private Check() {}

	interface Match {
		Match Ok = new Match() {
			@Override
			public boolean ok() {
				return true;
			}
		};

		boolean ok();

		interface Decomposition extends Match {
			@Override
			default boolean ok() {
				return false;
			}
			Match with(Arrow arrow);
		}
	}

	interface Transformation {
		Transformation IDEN = new Transformation() {};

	}

	static class Matcher<T extends Type> implements Visitor<T, T> {
		@Override
		public T empty(Empty e, T in) {
			return in.accept(new Visitor<Empty, T>() {
				@Override
				public T empty(Empty actual, Empty expected) {
					return Visitor.super.empty(e, in);
				}
			}, e);
		}
	}
}
