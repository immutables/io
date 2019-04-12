package io.immutables.that;

import org.junit.Test;
import static io.immutables.that.Assert.*;

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
}
