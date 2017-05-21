package io.immutables.that;

import java.util.Arrays;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Assert.that
 */
public final class Assert {
	private Assert() {}

	@CheckReturnValue
	public static That.Condition that() {
		class Tested implements That.Condition {}
		return new Tested();
	}

	@CheckReturnValue
	public static That.Runnable that(Runnable actualRuns) {
		class Tested extends That.Support<Runnable, That.Runnable> implements That.Runnable {}
		return new Tested().set(actualRuns);
	}

	@CheckReturnValue
	public static <T> That.Object<T> that(@Nullable T actual) {
		class Tested extends That.Support<T, That.Object<T>> implements That.Object<T> {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static <T> That.Optional<T> that(java.util.Optional<T> actual) {
		class Tested extends That.Support<java.util.Optional<T>, That.Optional<T>> implements That.Optional<T> {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static <T> That.Iterable<T> that(java.lang.Iterable<T> actual) {
		class Tested extends That.Support<java.lang.Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	@SafeVarargs
	public static <T> That.Iterable<T> that(T... actual) {
		class Tested extends That.Support<java.lang.Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(Arrays.asList(actual));
	}

	@CheckReturnValue
	public static That.String that(String actual) {
		class Tested extends That.Support<String, That.String> implements That.String {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Boolean that(boolean actual) {
		class Tested extends That.Support<Boolean, That.Boolean> implements That.Boolean {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Double that(double actual) {
		class Tested extends That.Support<Double, That.Double> implements That.Double {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Long that(long actual) {
		class Tested extends That.Support<Long, That.Long> implements That.Long {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Int that(int actual) {
		class Tested extends That.Support<Integer, That.Int> implements That.Int {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Boolean that(Boolean actual) {
		class Tested extends That.Support<Boolean, That.Boolean> implements That.Boolean {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Double that(Double actual) {
		class Tested extends That.Support<Double, That.Double> implements That.Double {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Long that(Long actual) {
		class Tested extends That.Support<Long, That.Long> implements That.Long {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Int that(Integer actual) {
		class Tested extends That.Support<Integer, That.Int> implements That.Int {}
		return new Tested().set(actual);
	}
}
