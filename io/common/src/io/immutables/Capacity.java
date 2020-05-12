package io.immutables;

import java.util.Arrays;

/**
 * Ensures capacity for arrays by nearest larger power of 2 increase in size and trying to avoid
 * oveflows until max size for array is reached.
 */
public final class Capacity {
	private Capacity() {}

	public static int[] ensure(int[] elements, int limit, int increment) {
		int oldCapacity = elements.length;
		// check is made this way to avoid overflow
		if (oldCapacity - limit >= increment) return elements;

		int requiredCapacity = oldCapacity + increment;
		int newCapacity;
		// checking for overflow
		if (requiredCapacity < oldCapacity) {
			newCapacity = Integer.MAX_VALUE;
		} else {
			newCapacity = oldCapacity << 1;
			if (newCapacity < requiredCapacity) {
				newCapacity = Integer.highestOneBit(requiredCapacity - 1) << 1;
			}
			if (newCapacity == 0) {
				newCapacity = requiredCapacity;
			} else if (newCapacity < 0) {
				newCapacity = Integer.MAX_VALUE;
			}
		}
		return Arrays.copyOf(elements, newCapacity);
	}
	
	public static char[] ensure(char[] elements, int limit, int increment) {
		int oldCapacity = elements.length;
		// check is made this way to avoid overflow
		if (oldCapacity - limit >= increment) return elements;

		int requiredCapacity = oldCapacity + increment;
		int newCapacity;
		// checking for overflow
		if (requiredCapacity < oldCapacity) {
			newCapacity = Integer.MAX_VALUE;
		} else {
			newCapacity = oldCapacity << 1;
			if (newCapacity < requiredCapacity) {
				newCapacity = Integer.highestOneBit(requiredCapacity - 1) << 1;
			}
			if (newCapacity == 0) {
				newCapacity = requiredCapacity;
			} else if (newCapacity < 0) {
				newCapacity = Integer.MAX_VALUE;
			}
		}
		return Arrays.copyOf(elements, newCapacity);
	}

	public static long[] ensure(long[] elements, int limit, int increment) {
		int oldCapacity = elements.length;
		// check is made this way to avoid overflow
		if (oldCapacity - limit >= increment) return elements;

		int requiredCapacity = oldCapacity + increment;
		int newCapacity;
		// checking for overflow
		if (requiredCapacity < oldCapacity) {
			newCapacity = Integer.MAX_VALUE;
		} else {
			newCapacity = oldCapacity << 1;
			if (newCapacity < requiredCapacity) {
				newCapacity = Integer.highestOneBit(requiredCapacity - 1) << 1;
			}
			if (newCapacity == 0) {
				newCapacity = requiredCapacity;
			} else if (newCapacity < 0) {
				newCapacity = Integer.MAX_VALUE;
			}
		}
		return Arrays.copyOf(elements, newCapacity);
	}

	public static <T> T[] ensure(T[] elements, int limit, int increment) {
		int oldCapacity = elements.length;
		// check is made this way to avoid overflow
		if (oldCapacity - limit >= increment) return elements;

		int requiredCapacity = oldCapacity + increment;
		int newCapacity;
		// checking for overflow
		if (requiredCapacity < oldCapacity) {
			newCapacity = Integer.MAX_VALUE;
		} else {
			newCapacity = oldCapacity << 1;
			if (newCapacity < requiredCapacity) {
				newCapacity = Integer.highestOneBit(requiredCapacity - 1) << 1;
			}
			if (newCapacity == 0) {
				newCapacity = requiredCapacity;
			} else if (newCapacity < 0) {
				newCapacity = Integer.MAX_VALUE;
			}
		}
		return Arrays.copyOf(elements, newCapacity);
	}
}
