package io.immutables.lang.type;

import io.immutables.collect.Vect;
import java.util.function.Function;
import static java.lang.System.out;
import static io.immutables.lang.type.Productions.*;

interface Play {
	Function<String, Type.Basic> typeFactory = Types.basicFactory();
	Type.Basic i32 = typeFactory.apply("i32");

	Type.Tystructor BidderCons = Types.typeConstructorId("Bidder");
	Type Bidder = BidderCons.construct(Vect.of());
	Type.Basic String = typeFactory.apply("String");

	static void main(String... args) {

		var exp = product(
				plus(access("a"), number(1)),
				apply(access("b"), "bid", number(2)),
				apply(access("b"), "zud", number(3)));

		out.println(exp);

		var scope = Scope.newTop();

		scope.put("a", i32);
		scope.put("b", Bidder);

		var T = Types.allocate("T");

		var registry = new Features.Registry()
				.put(BidderCons, "bid", Vect.of(), i32, String)
				.put(BidderCons, "zud", Vect.of(T), T, T);

		var typecheck = new Typecheck(scope, registry);
		typecheck.perform(exp);
	}
}
