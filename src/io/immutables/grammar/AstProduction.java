package io.immutables.grammar;

import org.immutables.value.Value.Auxiliary;

public interface AstProduction {
	@Auxiliary
	int beginTokenIndex();
	@Auxiliary
	int endTokenIndex();

	public interface Builder {}

	class Id {
		private final String id;

		public Id(String id) {
			this.id = id;
		}

		@Override
		public String toString() {
			return id;
		}
	}
}
