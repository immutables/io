package io.immutables.that;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code Assert.that}: Minimalistic, extensible and attentive to details assertions.
 */
public final class Assert {
	private Assert() {}

	/**
	 * @return that condition
	 */
	@CheckReturnValue
	public static That.Condition that() {
		class Tested implements That.Condition {}
		return new Tested();
	}

	/**
	 * @param actualRuns runnable or lambda
	 * @return that runnable
	 */
	@CheckReturnValue
	public static That.Block that(CanThrow actualRuns) {
		class Tested extends That.What<CanThrow, That.Block> implements That.Block {}
		return new Tested().set(actualRuns);
	}

	public interface CanThrow {
		void run() throws Exception;
	}

	/**
	 * @param <T> actual object type
	 * @param actual object
	 * @return that object
	 */
	@CheckReturnValue
	public static <T> That.Object<T> that(@Nullable T actual) {
		class Tested extends That.What<T, That.Object<T>> implements That.Object<T> {}
		return new Tested().set(actual);
	}

	/**
	 * @param <T> actual object type
	 * @param actual optional object
	 * @return that optional
	 */
	@CheckReturnValue
	public static <T> That.Optional<T> that(Optional<T> actual) {
		class Tested extends That.What<Optional<T>, That.Optional<T>> implements That.Optional<T> {}
		return new Tested().set(actual);
	}

	/**
	 * @param <T> actual element type
	 * @param actual iterable object
	 * @return that iterable
	 */
	@CheckReturnValue
	public static <T> That.Iterable<T> that(Iterable<T> actual) {
		class Tested extends That.What<Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(actual);
	}

	/**
	 * @param <T> actual element type
	 * @param actual stream object
	 * @return that iterable
	 */
	@CheckReturnValue
	public static <T> That.Iterable<T> that(Stream<T> actual) {
		class Tested extends That.What<Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(actual.collect(Collectors.toList()));
	}

	/**
	 * @param <T> actual component type
	 * @param actual array object
	 * @return that iterable
	 */
	@CheckReturnValue
	@SafeVarargs
	public static <T> That.Iterable<T> that(T... actual) {
		class Tested extends That.What<Iterable<T>, That.Iterable<T>> implements That.Iterable<T> {}
		return new Tested().set(Arrays.asList(actual));
	}

	/**
	 * @param actual array object
	 * @return that iterable
	 */
	@CheckReturnValue
	public static That.Iterable<Integer> that(int[] actual) {
		class Tested extends That.What<Iterable<Integer>, That.Iterable<Integer>> implements That.Iterable<Integer> {}
		return new Tested().set(Arrays.stream(actual).mapToObj(x -> x).collect(Collectors.toList()));
	}

	/**
	 * @param actual array object
	 * @return that iterable
	 */
	@CheckReturnValue
	public static That.Iterable<Long> that(long[] actual) {
		class Tested extends That.What<Iterable<Long>, That.Iterable<Long>> implements That.Iterable<Long> {}
		return new Tested().set(Arrays.stream(actual).mapToObj(x -> x).collect(Collectors.toList()));
	}

	/**
	 * @param actual array object
	 * @return that iterable
	 */
	@CheckReturnValue
	public static That.Iterable<Double> that(double[] actual) {
		class Tested extends That.What<Iterable<Double>, That.Iterable<Double>> implements That.Iterable<Double> {}
		return new Tested().set(Arrays.stream(actual).mapToObj(x -> x).collect(Collectors.toList()));
	}

	/**
	 * @param actual long value or null
	 * @return that double
	 */
	@CheckReturnValue
	public static That.String that(@Nullable String actual) {
		class Tested extends That.What<String, That.String> implements That.String {}
		return new Tested().set(actual);
	}

	/**
	 * @param actual long value or null
	 * @return that double
	 */
	@CheckReturnValue
	public static That.Boolean that(@Nullable Boolean actual) {
		class Tested extends That.What<Boolean, That.Boolean> implements That.Boolean {}
		return new Tested().set(actual);
	}

	/**
	 * @param actual long value or null
	 * @return that double
	 */
	@CheckReturnValue
	public static That.Double that(@Nullable Double actual) {
		class Tested extends That.What<Double, That.Double> implements That.Double {}
		return new Tested().set(actual);
	}

	/**
	 * @param actual long value or null
	 * @return that long
	 */
	@CheckReturnValue
	public static That.Long that(@Nullable Long actual) {
		class Tested extends That.What<Long, That.Long> implements That.Long {}
		return new Tested().set(actual);
	}

	/**
	 * @param actual int value or null
	 * @return that integer
	 */
	@CheckReturnValue
	public static That.Int that(@Nullable Integer actual) {
		class Tested extends That.What<Integer, That.Int> implements That.Int {}
		return new Tested().set(actual);
	}

	/**
	 * @param actual short value or null
	 * @return that integer
	 */
	@CheckReturnValue
	public static That.Int that(@Nullable Short actual) {
		return that(actual == null ? null : actual.intValue());
	}

	/**
	 * @param actual character value or null
	 * @return that integer
	 */
	@CheckReturnValue
	public static That.Int that(@Nullable Character actual) {
		return that(actual == null ? null : (int) actual);
	}

	/**
	 * @param actual float value or null
	 * @return that double
	 */
	@CheckReturnValue
	public static That.Double that(@Nullable Float actual) {
		return that(actual == null ? null : actual.doubleValue());
	}
}
