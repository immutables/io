package io.immutables.collect;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestVect {
	@Test
	public void construct() {
		that(Vect.of(1, 2, 3)).isOf(1, 2, 3);
		that(Vect.of()).isEmpty();
		that(Vect.of()).just().same(Vect.of());
		that(Vect.of(1, 2, 3, 4, 5)).hasSize(5);
		that(Vect.of(1, 2, 3, 4, 5)).just().hasToString("[1, 2, 3, 4, 5]");
	}

	@Test
	public void build() {
		that(Vect.<Integer>builder()
				.add(1)
				.add(2)
				.add(3)
				.build()).isOf(1, 2, 3);

		that(Vect.<Integer>builder()
				.addAll(Arrays.asList(1, 2, 3))
				.addAll(Collections.singleton(4))
				.build()).isOf(1, 2, 3, 4);
	}

	@Test
	public void fold() {
		that(Vect.of(1, 1, 1).fold(10, (a, b) -> a + b)).is(13);
		that(Vect.of(2, 2, 2).reduce((a, b) -> a * b)).is(8);
	}
}
