package io.immutables.that;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/**
 * @param <T> object under test type
 * @param <S> matcher type
 */
public interface That<T, S extends That<T, S>> {
	/**
	 * This is not a matcher method. Always throws {@link UnsupportedOperationException}.
	 * @deprecated Use {@link Object#equalTo(java.lang.Object)} instead.
	 */
	@Deprecated
	@Override
	boolean equals(java.lang.Object obj);

	/**
	 * This is not a matcher method. Always throws {@link UnsupportedOperationException}.
	 * @deprecated don't use this method
	 */
	@Deprecated
	@Override
	int hashCode();

	/** Turn matcher into a plain object matcher. */
	default Object<T> just() {
		return Assert.that(What.getNullable(this));
	}

	public interface Condition extends That<Void, Condition> {
		/**
		 * Fails if condition is {@code false}.
		 * @param condition to check
		 */
		default void is(boolean condition) {
			if (!condition) throw What.newAssertionError("expected true condition", "actual false");
		}

		/**
		 * Fails if condition is {@code true}
		 * @param condition to check
		 */
		default void not(boolean condition) {
			if (condition) throw What.newAssertionError("expected false condition", "actual true");
		}

		/**
		 * Fails if invoked. Should be used for unreachable during successful pass test code.
		 * 
		 * <pre>
		 * try {
		 *  somethingThatThrows();
		 *  that().unreachable();
		 * } catch (Exception ex) {
		 * </pre>
		 * 
		 * @see Assert#that(Runnable)
		 */
		default void unreachable() {
			throw What.newAssertionError("expected unreachable", "...and yet we are here");
		}
	}

	public interface Runnable extends That<java.lang.Runnable, Runnable> {
		default <E extends Throwable> That.Object<E> thrown(Class<E> thrownType) {
			try {
				java.lang.Runnable runnable = What.get(this);
				runnable.run();
				throw What.newAssertionError(
						"expected thrown " + thrownType.getCanonicalName(),
						"actual nothing thrown");
			} catch (Throwable throwable) {
				if (thrownType.isInstance(throwable)) {
					return Assert.that(thrownType.cast(throwable));
				}
				throw What.newAssertionError(
						"expected thrown " + thrownType.getCanonicalName(),
						"actual: " + throwable.getClass().getCanonicalName() + ": " + throwable.getMessage());
			}
		}
	}

	interface String extends That<java.lang.String, String> {

		default void is(java.lang.String expected) {
			java.lang.String actual = What.get(this);
			if (!expected.equals(actual)) {
				List<java.lang.String> diff = Diff.diff(expected, actual);
				throw What.newAssertionError("expected: " + diff.get(0), "actual: " + diff.get(1));
			}
		}

		default void isEmpty() {
			java.lang.String actual = What.get(this);
			if (!actual.isEmpty()) {
				throw What.newAssertionError("expected empty string", "actual: " + Diff.trim(actual));
			}
		}

		default void notEmpty() {
			java.lang.String actual = What.get(this);
			if (actual.isEmpty()) {
				throw What.newAssertionError("expected nonempty string", "actual empty");
			}
		}

		default void hasLength(int expectedLength) {
			java.lang.String actual = What.get(this);
			if (actual.length() != expectedLength) {
				throw What.newAssertionError(
						"expected length: " + expectedLength,
						"actual length: " + actual.length() + " — " + Diff.trim(What.get(this)));
			}
		}

		default void contains(java.lang.String substring) {
			java.lang.String actual = What.get(this);
			if (!actual.contains(substring)) {
				throw What.newAssertionError(
						"expected string containing: " + Diff.trim(substring),
						"actual: " + Diff.trim(actual));
			}
		}

		default void startsWith(java.lang.String prefix) {
			java.lang.String actual = What.get(this);
			if (!actual.startsWith(prefix)) {
				throw What.newAssertionError(
						"expected string starts with: " + Diff.trim(prefix),
						"actual: " + Diff.diff(actual, prefix).get(0));
			}
		}

		default void endsWith(java.lang.String suffix) {
			java.lang.String actual = What.get(this);
			if (!actual.endsWith(suffix)) {
				throw What.newAssertionError(
						"expected string starts with: " + Diff.trim(suffix),
						"actual: " + Diff.diff(actual, suffix).get(0));
			}
		}

		default void matches(java.lang.String regex) {
			java.lang.String actual = What.get(this);
			try {
				if (!actual.matches(regex)) {
					throw What.newAssertionError(
							"expected string to match /" + regex + "/",
							"actual: " + actual);
				}
			} catch (PatternSyntaxException ex) {
				throw What.newAssertionError(
						"expected string to match /" + regex + "/",
						"pattern syntax error: " + ex.getMessage().split("\\n"));
			}
		}
	}

	interface Boolean extends That<java.lang.Boolean, Boolean> {
		default void is(boolean trueOfFalse) {
			java.lang.Boolean b = What.get(this);
			if (trueOfFalse != b) {
				throw What.newAssertionError("expected: " + trueOfFalse, "actual: " + b);
			}
		}

		default void orFail(java.lang.String message) {
			if (!What.get(this)) {
				throw What.newAssertionError(message);
			}
		}
	}

	interface Object<T> extends That<T, Object<T>> {

		/**
		 * @deprecated Already regular object matcher.
		 * @return always {@code this}
		 */
		@Deprecated
		default Object<T> just() {
			return this;
		}

		default void isNull() {
			@Nullable T actual = What.getNullable(this);
			if (actual != null) {
				throw What.newAssertionError("expected: null", "actual: " + actual);
			}
		}

		default Object<T> notNull() {
			What.get(this);
			return this;
		}

		default <C extends T> Object<C> instanceOf(Class<C> type) {
			T actualRef = What.get(this);
			if (type.isInstance(actualRef)) {
				throw What.newAssertionError(
						"expected instance of " + type.getCanonicalName(),
						"actual: " + What.showReference(actualRef) + What.showToStringDetail(actualRef));
			}
			@SuppressWarnings("unchecked") // safe cast after runtime check
			Object<C> that = (Object<C>) this;
			return that;
		}

		default Object<T> is(Predicate<T> predicate) {
			@Nullable T actual = What.getNullable(this);
			if (!predicate.test(actual)) {
				throw What.newAssertionError(
						"expected matching predicate " + What.showNonDefault(predicate),
						"actual: " + actual);
			}
			return this;
		}

		default void same(@Nullable T expected) {
			@Nullable T actual = What.getNullable(this);
			if (actual != expected) {
				throw What.newAssertionError(
						"expected: "
								+ What.showReference(expected)
								+ What.showToStringDetail(expected),
						"actual: " + What.showReference(actual) + What.showToStringDetail(actual));
			}
		}

		default void notSame(@Nullable T expected) {
			@Nullable T actual = What.getNullable(this);
			if (actual == expected) {
				throw What.newAssertionError(
						"expected not same: " + What.showReference(expected) + What.showToStringDetail(expected),
						"actually was the same reference");
			}
		}

		default void hasToString(java.lang.String expectedToString) {
			Objects.requireNonNull(expectedToString);

			java.lang.String actual = What.get(this).toString();
			if (!actual.equals(expectedToString)) {
				List<java.lang.String> diff = Diff.diff(expectedToString, actual);
				throw What.newAssertionError("expected: " + diff.get(0), "actual: " + diff.get(1));
			}
		}

		default void equalTo(T expected) {
			Objects.requireNonNull(expected);

			T actual = What.get(this);
			if (!actual.equals(expected)) {
				java.lang.String as = actual.toString();
				java.lang.String es = expected.toString();

				if (as.equals(es)) {
					throw What.newAssertionError(
							"expected: " + What.showReference(expected) + What.showToStringDetail(expected),
							"actual: " + What.showReference(actual) + What.showToStringDetail(actual));
				}

				List<java.lang.String> diff = Diff.diff(es, as);
				throw What.newAssertionError("expected: " + diff.get(0), "actual: " + diff.get(1));
			}
		}

		default void notEqual(T expectedEquivalent) {
			Objects.requireNonNull(expectedEquivalent);

			T actual = What.get(this);
			if (actual.equals(expectedEquivalent)) {
				if (actual == expectedEquivalent) {
					throw What.newAssertionError(
							"expected not equal: "
									+ What.showReference(expectedEquivalent)
									+ What.showToStringDetail(expectedEquivalent),
							"actual was the same object");
				}

				java.lang.String as = actual.toString();
				java.lang.String es = expectedEquivalent.toString();

				if (as.equals(es)) {
					throw What.newAssertionError(
							What.showReference(expectedEquivalent) + What.showToStringDetail(expectedEquivalent),
							What.showReference(actual) + What.showToStringDetail(actual));
				}

				throw What.newAssertionError(
						What.showReference(expectedEquivalent) + What.showToStringDetail(expectedEquivalent),
						What.showReference(actual) + What.showToStringDetail(actual));
			}
		}
	}

	interface Optional<T> extends That<java.util.Optional<T>, Optional<T>> {

		default void isEmpty() {
			java.util.Optional<T> actual = What.get(this);
			if (actual.isPresent()) {
				throw What.newAssertionError(
						"expected: Optional.empty()",
						"actual: Optional.of(" + actual.get() + ")");
			}
		}

		default void isPresent() {
			java.util.Optional<T> actual = What.get(this);
			if (!actual.isPresent()) {
				throw What.newAssertionError(
						"expected: expected present Optional",
						"actual: Optional.empty()");
			}
		}

		default void isOf(T expected) {
			java.util.Optional<T> actualOptional = What.get(this);
			java.util.Optional<T> expectedOptional = java.util.Optional.of(expected);
			if (!actualOptional.equals(expectedOptional)) {
				throw What.newAssertionError(
						"expected: Optional.of(" + expected + ")",
						"actual: " + (actualOptional.isPresent()
								? "Optional.of(" + actualOptional.get() + ")"
								: "Optional.empty()"));
			}
		}
	}

	interface Iterable<T> extends That<java.lang.Iterable<T>, Iterable<T>> {
		default void notEmpty() {
			List<T> list = What.getList(this);
			if (list.isEmpty()) {
				throw What.newAssertionError("expected non empty", "actual: " + Diff.trim(What.get(this)));
			}
		}

		default void isEmpty() {
			List<T> list = What.getList(this);
			if (!list.isEmpty()) {
				throw What.newAssertionError("expected empty", "actual: " + Diff.trim(What.get(this)));
			}
		}

		default void hasSize(int expectedSize) {
			List<T> list = What.getList(this);
			if (list.size() != expectedSize) {
				throw What.newAssertionError(
						"expected size: " + expectedSize,
						"actual size: " + list.size() + " — " + Diff.trim(What.get(this)));
			}
		}

		default void has(T expectedElement) {
			List<T> list = What.getList(this);
			if (!list.contains(expectedElement)) {
				throw What.newAssertionError(
						"expected element: " + expectedElement,
						"actual none — " + Diff.trim(What.get(this)));
			}
		}

		default void isOf(java.lang.Iterable<T> expectedElements) {
			List<T> actualList = What.getList(this);
			List<T> expectedList = new ArrayList<>();
			expectedElements.forEach(expectedList::add);

			if (!actualList.equals(expectedList)) {
				List<java.lang.String> diff = Diff.diff(
						What.showElements(expectedList),
						What.showElements(actualList));

				throw What.newAssertionError(
						"expected elements: " + diff.get(0),
						"actual elements: " + diff.get(1));
			}
		}

		default void isOf(@SuppressWarnings("unchecked") T... expectedElements) {
			isOf(Arrays.asList(expectedElements));
		}

		default void hasAll(@SuppressWarnings("unchecked") T... expectedElements) {
			hasAll(Arrays.asList(expectedElements));
		}

		default void hasAll(java.lang.Iterable<T> expectedElements) {
			List<T> actualElements = What.getList(this);
			List<T> missingElements = new ArrayList<>();
			for (T e : expectedElements) {
				if (!actualElements.contains(e)) {
					missingElements.add(e);
				}
			}
			if (!missingElements.isEmpty()) {
				throw What.newAssertionError(
						"expected has all: " + What.showElements(expectedElements),
						"actual: missing " + What.showElements(missingElements) + " — " + Diff.trim(actualElements));
			}
		}

		default void hasOnly(@SuppressWarnings("unchecked") T... expectedElements) {
			hasOnly(Arrays.asList(expectedElements));
		}

		default void hasOnly(java.lang.Iterable<T> elements) {
			List<T> remainingElements = What.getList(this);
			List<T> expectedElements = new ArrayList<>();
			List<T> missingElements = new ArrayList<>();

			for (T e : elements) {
				expectedElements.add(e);
				if (!remainingElements.contains(e)) {
					missingElements.add(e);
				} else {
					remainingElements.remove(e);
				}
			}

			if (!missingElements.isEmpty() || !remainingElements.isEmpty()) {
				java.lang.String actual = "";
				if (!missingElements.isEmpty()) {
					actual += "missing " + What.showElements(missingElements) + "; ";
				}
				if (!remainingElements.isEmpty()) {
					actual += "extra  " + What.showElements(remainingElements);
				}
				throw What.newAssertionError(
						"expected only: " + What.showElements(expectedElements),
						"actual: " + actual);
			}
		}
	}

	interface Double extends That<java.lang.Double, Double> {

		default void bitwiseIs(double expected) {
			double actual = What.get(this);
			if (java.lang.Double.doubleToRawLongBits(actual) != java.lang.Double.doubleToRawLongBits(expected)) {
				throw What.newAssertionError("expected: " + expected, "actual: " + actual);
			}
		}

		default void is(DoublePredicate predicate) {
			double actual = What.get(this);
			if (!predicate.test(actual)) {
				throw What.newAssertionError(
						"expected matching predicate " + What.showNonDefault(predicate),
						"actual: " + actual);
			}
		}

		default void withinOf(double epsilon, double expected) {
			double actual = What.get(this);
			if (Math.abs(actual - expected) > epsilon) {
				throw What.newAssertionError(
						"expected within ±" + epsilon + " of " + expected,
						"actual: " + actual);
			}
		}

		default void isNaN() {
			double actual = What.get(this);
			if (!java.lang.Double.isNaN(actual)) {
				throw What.newAssertionError("expected NaN", "actual: " + actual);
			}
		}

		default void isFinite() {
			double actual = What.get(this);
			if (!java.lang.Double.isFinite(actual)) {
				throw What.newAssertionError("expected finite double", "actual: " + actual);
			}
		}

		default void isInfinite() {
			double actual = What.get(this);
			if (!java.lang.Double.isInfinite(actual)) {
				throw What.newAssertionError("expected infinite double", "actual: " + actual);
			}
		}
	}

	interface Int extends That<java.lang.Integer, Int> {
		default void is(IntPredicate predicate) {
			int actual = What.get(this);
			if (!predicate.test(actual)) {
				throw What.newAssertionError(
						"expected matching predicate " + What.showNonDefault(predicate),
						"actual: " + actual);
			}
		}

		default void is(int expected) {
			int actual = What.get(this);
			if (actual != expected) {
				throw What.newAssertionError("expected: " + expected, "actual: " + actual);
			}
		}
	}

	interface Long extends That<java.lang.Long, Long> {
		default void is(LongPredicate predicate) {
			long actual = What.get(this);
			if (!predicate.test(actual)) {
				throw What.newAssertionError(
						"expected matching predicate " + What.showNonDefault(predicate),
						"actual: " + actual);
			}
		}

		default void is(long expected) {
			long actual = What.get(this);
			if (actual != expected) {
				throw What.newAssertionError("expected: " + expected, "actual: " + actual);
			}
		}
	}

	public abstract class What<T, S extends That<T, S>> implements That<T, S> {
		private @Nullable T value;
		private boolean isSet;

		/**
		 * Set value under test
		 * @param value to be tested
		 * @return this for chained invokation
		 */
		@SuppressWarnings("unchecked")
		public final S set(@Nullable T value) {
			this.value = value;
			this.isSet = true;
			return (S) this;
		}

		private @Nullable T get() {
			if (!isSet) throw new IllegalStateException("What.value is not set");
			return value;
		}

		@Deprecated
		@Override
		public final boolean equals(java.lang.Object obj) {
			throw new UnsupportedOperationException();
		}

		@Deprecated
		@Override
		public final int hashCode() {
			throw new UnsupportedOperationException();
		}

		@Override
		public final java.lang.String toString() {
			return "What(" + value + ")";
		}

		public static <T, S extends That<T, S>> T get(That<T, S> that) {
			@Nullable T value = ((What<T, S>) that).get();
			if (value != null) return value;
			throw newAssertionError("non-null expected");
		}

		public static <T, S extends That<T, S>> T getNullable(That<T, S> that) {
			return ((What<T, S>) that).get();
		}

		public static java.lang.AssertionError newAssertionError(java.lang.String... mismatch) {
			return new AssertionError(mismatch);
		}

		private static <T, S extends That<java.lang.Iterable<T>, S>> List<T> getList(That<java.lang.Iterable<T>, S> that) {
			java.lang.Iterable<T> actual = get(that);
			List<T> list = new ArrayList<>();
			actual.forEach(list::add);
			return list;
		}

		private static java.lang.String showElements(java.lang.Iterable<?> iterable) {
			return StreamSupport.stream(iterable.spliterator(), false)
					.map(Objects::toString)
					.collect(Collectors.joining(", "));
		}

		private static java.lang.String showNonDefault(java.lang.Object ref) {
			java.lang.String string = ref.toString();
			if (string.endsWith(identityHashCodeSuffix(ref))) return "";
			return string;
		}

		private static java.lang.String showReference(java.lang.Object ref) {
			return ref == null ? "null" : (ref.getClass().getSimpleName() + identityHashCodeSuffix(ref));
		}

		private static java.lang.String identityHashCodeSuffix(java.lang.Object ref) {
			return "@" + Integer.toHexString(System.identityHashCode(ref));
		}

		private static java.lang.String showToStringDetail(java.lang.Object ref) {
			return ref == null ? "" : (" — " + ref);
		}
	}
}
