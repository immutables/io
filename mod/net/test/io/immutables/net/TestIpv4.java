package io.immutables.net;

import io.immutables.net.Ip4.Mask;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestIpv4 {

	@Test
	public void address() {
		that(Ip4.loopback().isLoopback()).is(true);
		that(Ip4.parseAddress("127.0.0.1").isLoopback()).is(true);
		that(Ip4.loopback()).equalTo(Ip4.address(127, 0, 0, 1));
		that(Ip4.address(0).isAnyLocal()).is(true);
		that(Ip4.anyLocal().isLoopback()).is(false);
		that(Ip4.anyLocal().isAnyLocal()).is(true);
		that(Ip4.address(new byte[] {127, 127, 127, 127})).equalTo(Ip4.address(127, 127, 127, 127));
	}

	@Test
	public void mask() {
		Mask mask8 = Ip4.mask(8);

		that(mask8).hasToString("255.0.0.0");
		that(mask8.toBinaryString()).is("11111111.00000000.00000000.00000000");
		that(mask8.asCidrSuffix()).is("/8");

		that(mask8).equalTo(Ip4.mask(8));
		that(mask8).notEqual(Ip4.mask(16));
	}

	@Test
	public void subnet() {
		that(Ip4.loopback().withMask(20).hosts()).is(4096);
		that(Ip4.address(172, 64, 1, 1).withMask(16)).hasToString("172.64.0.0/16");
		that(Ip4.parseSubnet("172.64.0.0/16")).hasToString("172.64.0.0/16");
		that(Ip4.parseSubnet("172.64.0.0/16")).equalTo(Ip4.address(172, 64, 1, 1).withMask(16));

		that(Ip4.address(127, 0, 0, 0).withMask(30).asHostSet()).isOf(
				Ip4.address(127, 0, 0, 0),
				Ip4.address(127, 0, 0, 1),
				Ip4.address(127, 0, 0, 2),
				Ip4.address(127, 0, 0, 3));
	}

	@Test
	public void subnetRange() {
		that(Ip4.parseSubnet("172.96.0.0/16").rangeSubnets(18)).isOf(
				Ip4.address(172, 96, 0, 0).withMask(18),
				Ip4.address(172, 96, 64, 0).withMask(18),
				Ip4.address(172, 96, 128, 0).withMask(18),
				Ip4.address(172, 96, 192, 0).withMask(18));
	}
}
