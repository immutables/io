package io.immutables.lang.node;

class FeatureArrow {
	/** feature name */
	String name;
	/** if feature is not found */
	boolean missing;
	/** all type variables to be resolved */
	Typed[] variables;
	/** type of receiver */
	Typed on;
	/** out/return type */
	Typed out;
	/** input type */
	Typed in;
	/** constraints on the feature, includes those applicable to type variables */
	Constraint[] constraints;

	// not now...
	enum Kind {
		Binding,
		Named,
		Operator,
	}
}
