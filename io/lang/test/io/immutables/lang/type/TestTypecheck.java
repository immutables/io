package io.immutables.lang.type;

import org.junit.Test;
import static io.immutables.lang.type.FixtureTypes.*;
import static io.immutables.that.Assert.that;

public class TestTypecheck {

	@Test
	public void checkEqual() {
		typecheck(i32, i32);
		typecheck(Empty, Empty);
		typecheck(productOf(Empty, i32), productOf(Empty, i32));
		typecheck(Aa_T.instantiate(str), Aa_T.instantiate(str));
	}

	@Test
	public void mismatchProduct() {
		that(() -> {
			typecheck(productOf(bool, i32), productOf(str, Empty));
		}).thrown(RuntimeException.class);
	}

	@Test
	public void matchParameterizedByAlias() {
		typecheck(Aa_T.instantiate(J), Aa_T.instantiate(L));
	}

	@Test
	public void matchParameterizedBySubstitute() {
		typecheck(Aa_T.instantiate(J), Aa_T.instantiate(i32));
	}

	@Test
	public void mismatchParameterized() {
		that(() -> {
			typecheck(Aa_T.instantiate(i32), Aa_T.instantiate(bool));
		}).thrown(RuntimeException.class);
	}

	private void typecheck(Type expected, Type actual) {
		Solver.typecheck(expected, actual);
	}
}
