package io.immutables.grammar;

import java.util.Arrays;

/**
 * Ensures capacity for int and long arrays with 2x increase in size and trying to avoid oveflows
 * until max size for array is reached.
 */
final class Capacity {
	private Capacity() {}

	static int[] ensure(int[] elements, int limit, int increment) {
		int oldCapacity = elements.length;
		// check is made this way to avoid overflow
		if (oldCapacity - limit < increment) {
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
				if (newCapacity < 0) {
					newCapacity = Integer.MAX_VALUE;
				}
			}
			elements = Arrays.copyOf(elements, newCapacity);
		}
		return elements;
	}

	static long[] ensure(long[] elements, int limit, int increment) {
		int oldCapacity = elements.length;
		// check is made this way to avoid overflow
		if (oldCapacity - limit < increment) {
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
				if (newCapacity < 0) {
					newCapacity = Integer.MAX_VALUE;
				}
			}
			elements = Arrays.copyOf(elements, newCapacity);
		}
		return elements;
	}
}
