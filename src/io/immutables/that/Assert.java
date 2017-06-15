package io.immutables.that;

import java.util.Arrays;
import java.util.stream.Collectors;
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
		class Tested extends That.What<Runnable, That.Runnable> implements That.Runnable {}
		return new Tested().set(actualRuns);
	}

	@CheckReturnValue
	public static <T> That.Object<T> that(@Nullable T actual) {
		class Tested extends That.What<T, That.Object<T>> implements That.Object<T> {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static <T> That.Optional<T> that(java.util.Optional<T> actual) {
		class Tested extends That.What<java.util.Optional<T>, That.Optional<T>> implements That.Optional<T> {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static <T> That.Iterable<T> that(java.lang.Iterable<T> actual) {
		class Tested extends That.What<java.lang.Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static <T> That.Iterable<T> that(java.util.stream.Stream<T> actual) {
		class Tested extends That.What<java.lang.Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(actual.collect(Collectors.toList()));
	}

	@CheckReturnValue
	@SafeVarargs
	public static <T> That.Iterable<T> that(T... actual) {
		class Tested extends That.What<java.lang.Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(Arrays.asList(actual));
	}

	@CheckReturnValue
	public static That.String that(@Nullable String actual) {
		class Tested extends That.What<String, That.String> implements That.String {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Boolean that(@Nullable Boolean actual) {
		class Tested extends That.What<Boolean, That.Boolean> implements That.Boolean {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Double that(@Nullable Double actual) {
		class Tested extends That.What<Double, That.Double> implements That.Double {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Long that(@Nullable Long actual) {
		class Tested extends That.What<Long, That.Long> implements That.Long {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Int that(@Nullable Integer actual) {
		class Tested extends That.What<Integer, That.Int> implements That.Int {}
		return new Tested().set(actual);
	}

	@CheckReturnValue
	public static That.Int that(@Nullable Short actual) {
		return that(actual == null ? null : actual.intValue());
	}

	@CheckReturnValue
	public static That.Int that(@Nullable Character actual) {
		return that(actual == null ? null : (int) actual.charValue());
	}

	@CheckReturnValue
	public static That.Double that(@Nullable Float actual) {
		return that(actual == null ? null : actual.doubleValue());
	}
}
