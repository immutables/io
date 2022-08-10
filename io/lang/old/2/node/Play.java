package io.immutables.lang.node;

import static io.immutables.lang.node.BuiltinTypes.*;
import static io.immutables.lang.node.Nodes.*;
import static java.lang.System.out;

interface Play {

	Tyctor AbcOfT = Tyctor.parameterized("Abc", "T");
	Typed ParamT = AbcOfT.getParameter("T");

	Tyctor BbzCtor = Tyctor.basic("Bbz");
	Typed Bbz = BbzCtor.instance();

	static void main(String... args) {
		var registry = new FeatureRegistry();
		registry.putAccess("a", i32);
		registry.putAccess("b", Bbz);

		var featureT = registry.putFeature(AbcOfT, "t");
		featureT.out(Types.product(ParamT, ParamT));

		var featureX = registry.putFeature(BbzCtor, "x");
		var SofX = featureX.parameter("S");
		featureX
				.in(SofX)
				.out(AbcOfT.instance(SofX))
				.done();

		registry.putFeature(BbzCtor, "w")
				.in(i32)
				.out(f32)
				.done();

		var node = product(
				plus(access("a"), number(1)),
				apply(access("a"), "t", empty()),
				apply(access("b"), "x", str("XYZ")),
				apply(access("b"), "w", number(3)));

		var check = new TypeCheck(registry);
		check.check(node);

		printTyped(node);
	}
}
