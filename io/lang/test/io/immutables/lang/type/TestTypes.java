package io.immutables.lang.type;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import static io.immutables.lang.type.FixtureTypes.*;
import static io.immutables.that.Assert.that;

public class TestTypes {
	@Test
	public void substitution() {
		var A = Type.Parameter.introduce("A", 0);
		var B = Type.Parameter.introduce("B", 1);

		var A_ = Type.Variable.allocate(A.name());
		var B_ = Type.Variable.allocate(B.name());

		var subs = ImmutableMap.of(A, A_, B, B_);
		var before = Type.Product.of(A, B);
		var after = before.transform(Types.substitution(subs));

		that(after).equalTo(Type.Product.of(A_, B_));
	}

	@Test
	public void signature() {
		System.out.println(productOf(bool, i32));
		System.out.println(Aa_T);
		System.out.println(Bb_W_Y);
	}
}
