package io.immutables.lang.type;

import java.util.HashMap;
import java.util.Map;

public interface Scope {

	class Builder {
		private final Map<Name, Type> types = new HashMap<>();
		private final Map<Name, Type.Concept> concepts = new HashMap<>();
		
		public Builder put(Name name, Type type) {
			types.put(name, type);
			return this;
		}
		
		public Builder add(Name name, Type.Concept concept) {
			concepts.put(name, concept);
			return this;
		}
	}
}
