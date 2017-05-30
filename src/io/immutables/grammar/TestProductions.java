package io.immutables.grammar;

import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestProductions {
	@Test
	public void bitsCodingPositive() {
		long l1 = 0, l2 = 0;

		l1 = Productions.encodeNextSibling(l1, 144);
		l1 = Productions.encodePart(l1, (short) 222);
		l1 = Productions.encodeKind(l1, (short) 5555);
		l2 = Productions.encodeTermBegin(l2, 121212);
		l2 = Productions.encodeTermEnd(l2, 545454);

		that(Productions.decodeNextSibling(l1)).is(144);
		that(Productions.decodePart(l1)).is(222);
		that(Productions.decodeKind(l1)).is(5555);
		that(Productions.decodeTermBegin(l2)).is(121212);
		that(Productions.decodeTermEnd(l2)).is(545454);
	}

	@Test
	public void bitsCodingNegative() {
		long l1 = 0, l2 = 0;

		l1 = Productions.encodeNextSibling(l1, -44);
		l1 = Productions.encodePart(l1, (short) -222);
		l1 = Productions.encodeKind(l1, (short) -5555);
		l2 = Productions.encodeTermBegin(l2, -121212);
		l2 = Productions.encodeTermEnd(l2, -545454);

		that(Productions.decodeNextSibling(l1)).is(-44);
		that(Productions.decodePart(l1)).is(-222);
		that(Productions.decodeKind(l1)).is(-5555);
		that(Productions.decodeTermBegin(l2)).is(-121212);
		that(Productions.decodeTermEnd(l2)).is(-545454);
	}
}
