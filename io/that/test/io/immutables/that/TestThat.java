package io.immutables.that;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestThat {
	@Test
	public void assertStringPassing() {
		that("a").is("a");
		that("").isEmpty();
	}

	@Test(expected = AssertionError.class)
	public void assertStringEqual() {
		that("a").is("b");
	}

	@Test(expected = AssertionError.class)
	public void assertStringEmpty() {
		that("a").isEmpty();
	}

	@Test(expected = AssertionError.class)
	public void assertFinite() {
		that(Double.POSITIVE_INFINITY).isFinite();
	}

	@Test(expected = AssertionError.class)
	public void assertNotNan() {
		that(Double.NaN).isFinite();
	}

	@Test(expected = AssertionError.class)
	public void assertNotNull() {
		that((Object) null).notNull();
	}

	@Test(expected = AssertionError.class)
	public void assertEmptyHasSize() {
		that(Collections.emptyList()).hasSize(1);
	}

	@Test(expected = AssertionError.class)
	public void assertJustSame() {
		that("a").just().same(new String("a"));
	}

	@Test(expected = AssertionError.class)
	public void assertHasOnly() {
		that(Arrays.asList("a", "b", "c", "d")).hasOnly("a", "b", "h", "d");
	}
}
