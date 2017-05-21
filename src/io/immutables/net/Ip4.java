package io.immutables.net;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedBytes;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Representations of IPv4 addresses, mask and subnets and operations between them.
 * Primary use-case is subnet ranging and calculations.
 */
public final class Ip4 extends DiscreteDomain<Ip4.Address> {
	private static final Ip4 DOMAIN = new Ip4();
	private Ip4() {}

	private static final Address LOOPBACK = address(127, 0, 0, 1);
	private static final Address ANY_LOCAL = address(0);
	private static final Address ANY_LOCAL_BROADCAST = address(-1);

	public static Address loopback() {
		return LOOPBACK;
	}

	public static Address anyLocal() {
		return ANY_LOCAL;
	}

	public static final class Address implements Comparable<Address> {
		public final int value;

		public Address(int value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return format(value, false);
		}

		public byte[] bytes() {
			return Ints.toByteArray(value);
		}

		public Subnet withMask(int bits) {
			return withMask(mask(bits));
		}

		public Subnet withMask(Mask mask) {
			return Subnet.from(this, mask);
		}

		public String toBinaryString() {
			return format(value, true);
		}

		public boolean isLoopback() {
			return value == LOOPBACK.value;
		}

		public boolean isAnyLocal() {
			return value == ANY_LOCAL.value;
		}

		@Override
		public int compareTo(Address o) {
			return value - o.value;
		}

		@Override
		public int hashCode() {
			return value;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Address
					&& ((Address) obj).value == value;
		}
	}

	public static final class Subnet implements Comparable<Subnet> {
		private final int prefix;
		private final int bits;

		private Subnet(int prefix, int bits) {
			this.prefix = prefix;
			this.bits = bits;
		}

		public Mask mask() {
			return Ip4.mask(bits);
		}

		public Address prefix() {
			return Ip4.address(bits);
		}

		private static Subnet from(Address prefix, Mask mask) {
			return new Subnet(prefix.value & mask.mask(), mask.bits);
		}

		public ImmutableSet<Subnet> rangeSubnets(int subnetBits) {
			return rangeSubnets(Ip4.mask(subnetBits));
		}

		public ImmutableSet<Subnet> rangeSubnets(Mask subnetMask) {
			Preconditions.checkArgument(subnetMask.bits > bits, "mask to range has to be more bits ");
			ImmutableSet.Builder<Subnet> builder = ImmutableSet.builder();

			int maxIncrement = -1 >>> Integer.SIZE - (subnetMask.bits - bits);

			for (int i = 0; i <= maxIncrement; i++) {
				int subnetIncrement = i << Integer.SIZE - subnetMask.bits;
				int subnetPrefix = prefix + subnetIncrement;
				builder.add(new Subnet(subnetPrefix, subnetMask.bits));
			}

			return builder.build();
		}

		/**
		 * Number of hosts possible in this network including any reserved hosts reserved for subnet (0,
		 * broadcast, etc).
		 * @return host count
		 */
		public int hosts() {
			return (-1 >>> bits) + 1;
		}

		@Override
		public int compareTo(Subnet o) {
			return ComparisonChain.start()
					.compare(prefix, o.prefix)
					.compare(bits, o.bits)
					.result();
		}

		@Override
		public int hashCode() {
			return (17 + prefix) * 31 + bits;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Subnet
					&& ((Subnet) obj).prefix == prefix
					&& ((Subnet) obj).bits == bits;
		}

		@Override
		public String toString() {
			return "" + address(prefix) + mask().asCidrSuffix();
		}

		public Range<Address> asHostRange() {
			return Range.closed(address(prefix), address(prefix + hosts() - 1));
		}

		public ContiguousSet<Address> asHostSet() {
			return ContiguousSet.create(asHostRange(), DOMAIN);
		}
	}

	public static final class Mask {
		public final int bits;

		private Mask(int bits) {
			this.bits = bits;
		}

		public int mask() {
			return -1 << (Integer.SIZE - bits);
		}

		@Override
		public String toString() {
			return format(mask(), false);
		}

		public String toBinaryString() {
			return format(mask(), true);
		}

		public String asCidrSuffix() {
			return "/" + bits;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Mask
					&& ((Mask) obj).bits == bits;
		}

		@Override
		public int hashCode() {
			return bits;
		}
	}

	public static Address parseAddress(String string) {
		List<String> numbers = Splitter.on('.').splitToList(string);
		byte[] bs = new byte[numbers.size()];
		for (int i = 0; i < bs.length; i++) {
			bs[i] = UnsignedBytes.parseUnsignedByte(numbers.get(i));
		}
		return address(bs);
	}

	public static Address address(byte[] bytes) {
		Preconditions.checkArgument(bytes.length == 4, "4 bytes expected for IPv4 address");
		return address(Ints.fromByteArray(bytes));
	}

	public static Address address(int address) {
		return new Address(address);
	}

	public static Subnet parseSubnet(String string) {
		int indexOfSlash = string.indexOf('/');
		Preconditions.checkArgument(indexOfSlash > 0, "CIDR /[bits] missing");
		try {
			String address = string.substring(0, indexOfSlash);
			String bits = string.substring(indexOfSlash + 1);

			return parseAddress(address)
					.withMask(Integer.parseInt(bits));

		} catch (Exception ex) {
			throw new IllegalStateException(ex.getMessage());
		}
	}

	public static Address address(int b1, int b2, int b3, int b4) {
		return address(Ints.fromBytes(
				UnsignedBytes.saturatedCast(b1),
				UnsignedBytes.saturatedCast(b2),
				UnsignedBytes.saturatedCast(b3),
				UnsignedBytes.saturatedCast(b4)));
	}

	public static Mask mask(int bits) {
		Preconditions.checkArgument(bits > 0 && bits < 32, "bits should be in range [1,31]");
		return new Mask(bits);
	}

	private static String format(int value, boolean binary) {
		byte[] bs = Ints.toByteArray(value);
		String[] parts = new String[4];

		for (int i = 0; i < parts.length; i++) {
			parts[i] = binary
					? Strings.padStart(UnsignedBytes.toString(bs[i], 2), Byte.SIZE, '0')
					: UnsignedBytes.toString(bs[i]);
		}

		return Joiner.on('.').join(parts);
	}

	@Override
	public Address maxValue() {
		return ANY_LOCAL_BROADCAST;
	}

	@Override
	public Address minValue() {
		return ANY_LOCAL;
	}

	@Override
	public long distance(Address start, Address end) {
		return end.value - start.value;
	}

	@Override
	public @Nullable Address next(Address value) {
		if (value.equals(maxValue())) return null;
		return address(value.value + 1);
	}

	@Override
	public @Nullable Address previous(Address value) {
		if (value.equals(minValue())) return null;
		return address(value.value - 1);
	}
}
