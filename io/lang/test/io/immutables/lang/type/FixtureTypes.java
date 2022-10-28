package io.immutables.lang.type;

interface FixtureTypes {
	Type.Terminal i32 = Type.Terminal.define("i32");
	Type.Terminal bool = Type.Terminal.define("bool");
	Type.Terminal str = Type.Terminal.define("str");

	Type.Variable J = Type.Variable.allocate("J");
	Type.Variable K = Type.Variable.allocate("K");
	Type.Variable L = Type.Variable.allocate("L");

	Type.Variable X = Type.Variable.allocate("X");
	Type.Variable Y = Type.Variable.allocate("Y");
	Type.Variable Z = Type.Variable.allocate("Z");

	Type.Product Empty = Type.Product.Empty;

	TypeConstructor Aa_T = TypeSignature.name("Aa", "T").constructor();
	TypeConstructor Bb_W_Y = TypeSignature.name("Bb", "W", "Y").constructor();

	static Type.Product productOf(Type c0, Type c1, Type... components) {
		return Type.Product.of(c0, c1, components);
	}
}
