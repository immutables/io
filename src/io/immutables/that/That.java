package io.immutables.that;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

public interface That<T, S extends That<T, S>> {

	public interface Condition extends That<Void, Condition> {
		default void is(boolean condition) {
			if (!condition) {
				throw new AssertionError("expected true condition", "actual false");
			}
		}

		default void not(boolean condition) {
			if (condition) {
				throw new AssertionError("expected false condition", "actual true");
			}
		}

		default void unreachable() {
			throw new AssertionError("expected unreachable", "and yet we are here");
		}
	}

	public interface Runnable extends That<java.lang.Runnable, Runnable> {
		default void thrown(Class<? extends Throwable> thrownType) {
			try {
				java.lang.Runnable runnable = Support.get(this);
				runnable.run();
				throw new AssertionError("expected thrown " + thrownType.getCanonicalName(), "actual nothing thrown");
			} catch (Throwable throwable) {
				if (!thrownType.isInstance(throwable)) {
					throw new AssertionError("expected thrown " + thrownType.getCanonicalName(), "actual: "
							+ throwable.getClass().getCanonicalName() + ": " + throwable.getMessage());
				}
			}
		}
	}

	default Object<T> just() {
		T object = Support.getNullable(this);
		class Tested extends Support<T, Object<T>> implements That.Object<T> {}
		return new Tested().set(object);
	}

	interface String extends That<java.lang.String, String> {

		default void is(java.lang.String expected) {
			java.lang.String actual = Support.get(this);
			if (!expected.equals(actual)) {
				List<java.lang.String> diff = Diff.diff(expected, actual);
				throw new AssertionError("expected: " + diff.get(0), "actual: " + diff.get(1));
			}
		}

		default void isEmpty() {
			java.lang.String actual = Support.get(this);
			if (!actual.isEmpty()) {
				throw new AssertionError("expected empty string", "actual: " + Diff.trim(actual));
			}
		}

		default void notEmpty() {
			java.lang.String actual = Support.get(this);
			if (actual.isEmpty()) {
				throw new AssertionError("expected nonempty string", "actual empty");
			}
		}

		default void hasLength(int expectedLength) {
			java.lang.String actual = Support.get(this);
			if (actual.length() != expectedLength) {
				throw new AssertionError(
						"expected length: " + expectedLength,
						"actual length: " + actual.length() + " — " + Diff.trim(Support.get(this)));
			}
		}

		default void contains(java.lang.String substring) {
			java.lang.String actual = Support.get(this);
			if (!actual.contains(substring)) {
				throw new AssertionError(
						"expected string containing: " + Diff.trim(substring),
						"actual: " + Diff.trim(actual));
			}
		}

		default void startsWith(java.lang.String prefix) {
			java.lang.String actual = Support.get(this);
			if (!actual.startsWith(prefix)) {
				throw new AssertionError(
						"expected string starts with: " + Diff.trim(prefix),
						"actual: " + Diff.diff(actual, prefix).get(0));
			}
		}

		default void endsWith(java.lang.String suffix) {
			java.lang.String actual = Support.get(this);
			if (!actual.endsWith(suffix)) {
				throw new AssertionError(
						"expected string starts with: " + Diff.trim(suffix),
						"actual: " + Diff.diff(actual, suffix).get(0));
			}
		}
	}

	interface Boolean extends That<java.lang.Boolean, Boolean> {
		default void is(boolean trueOfFalse) {
			java.lang.Boolean b = Support.get(this);
			if (trueOfFalse != b) {
				throw new AssertionError("expected: " + trueOfFalse, "actual: " + b);
			}
		}
		default void orFail(java.lang.String message) {
			if (!Support.get(this)) {
				throw new AssertionError(message);
			}
		}
	}

	interface Object<T> extends That<T, Object<T>> {
		default void nonnull() {
			Support.get(this);
		}

		default void isNull() {
			@Nullable java.lang.Object actualRef = Support.getNullable(this);
			if (actualRef != null) {
				throw new AssertionError("expected: " + null, "actual: " + actualRef);
			}
		}

		default void instanceOf(Class<? extends T> type) {
			java.lang.Object actualRef = Support.get(this);
			if (type.isInstance(actualRef)) {
				throw new AssertionError(
						"expected instance of " + type.getCanonicalName(),
						"actual: " + Support.printReference(actualRef) + Support.printToStringDetail(actualRef));
			}
		}

		default void same(@Nullable T expectedRef) {
			@Nullable T actualRef = Support.getNullable(this);
			if (actualRef != expectedRef) {
				throw new AssertionError("expected: "
						+ Support.printReference(expectedRef)
						+ Support.printToStringDetail(expectedRef),
						"actual: " + Support.printReference(actualRef) + Support.printToStringDetail(actualRef));
			}
		}

		default void notSame(@Nullable T expectedRef) {
			@Nullable T actualRef = Support.getNullable(this);
			if (actualRef == expectedRef) {
				throw new AssertionError(
						"expected not same: " + Support.printReference(expectedRef) + Support.printToStringDetail(expectedRef),
						"actually was the same reference");
			}
		}

		default void hasToString(java.lang.String expectedToString) {
			Objects.requireNonNull(expectedToString);

			java.lang.String actual = Support.get(this).toString();
			if (!actual.equals(expectedToString)) {
				List<java.lang.String> diff = Diff.diff(expectedToString, actual);
				throw new AssertionError("expected: " + diff.get(0), "actual: " + diff.get(1));
			}
		}

		default void equalTo(T expectedEquivalent) {
			Objects.requireNonNull(expectedEquivalent);

			T actual = Support.get(this);
			if (!actual.equals(expectedEquivalent)) {
				java.lang.String as = actual.toString();
				java.lang.String es = expectedEquivalent.toString();

				if (as.equals(es)) {
					throw new AssertionError("expected: "
							+ Support.printReference(expectedEquivalent)
							+ Support.printToStringDetail(expectedEquivalent),
							"actual: " + Support.printReference(actual) + Support.printToStringDetail(actual));
				}

				List<java.lang.String> diff = Diff.diff(es, as);
				throw new AssertionError("expected: " + diff.get(0), "actual: " + diff.get(1));
			}
		}

		default void notEqual(T expectedEquivalent) {
			Objects.requireNonNull(expectedEquivalent);

			T actual = Support.get(this);
			if (actual.equals(expectedEquivalent)) {
				if (actual == expectedEquivalent) {
					throw new AssertionError(
							"expected not equal: "
									+ Support.printReference(expectedEquivalent)
									+ Support.printToStringDetail(expectedEquivalent),
							"actual was the same object");
				}

				java.lang.String as = actual.toString();
				java.lang.String es = expectedEquivalent.toString();

				if (as.equals(es)) {
					throw new AssertionError(
							Support.printReference(expectedEquivalent) + Support.printToStringDetail(expectedEquivalent),
							Support.printReference(actual) + Support.printToStringDetail(actual));
				}

				throw new AssertionError(
						Support.printReference(expectedEquivalent) + Support.printToStringDetail(expectedEquivalent),
						Support.printReference(actual) + Support.printToStringDetail(actual));
			}
		}
	}

	interface Optional<T> extends That<java.util.Optional<T>, Optional<T>> {

		default void isEmpty() {
			java.util.Optional<T> actual = Support.get(this);
			if (actual.isPresent()) {
				throw new AssertionError(
						"expected: Optional.empty()",
						"actual: Optional.of(" + actual.get() + ")");
			}
		}

		default void isPresent() {
			java.util.Optional<T> actual = Support.get(this);
			if (!actual.isPresent()) {
				throw new AssertionError("expected: expected present Optional",
						"actual: actual: Optional.empty()");
			}
		}

		default void isOf(T expected) {
			java.util.Optional<T> actualOptional = Support.get(this);
			java.util.Optional<T> expectedOptional = java.util.Optional.of(expected);
			if (!actualOptional.equals(expectedOptional)) {
				throw new AssertionError(
						"expected: Optional.of(" + expected + ")",
						"actual: "
								+ (actualOptional.isPresent() ? "Optional.of(" + actualOptional.get() + ")" : "Optional.empty()"));
			}
		}
	}

	interface Iterable<T> extends That<java.lang.Iterable<T>, Iterable<T>> {
		default void notEmpty() {
			List<T> list = Support.getList(this);
			if (list.isEmpty()) {
				throw new AssertionError("expected non empty", "actual: " + Diff.trim(Support.get(this)));
			}
		}

		default void isEmpty() {
			List<T> list = Support.getList(this);
			if (!list.isEmpty()) {
				throw new AssertionError("expected empty", "actual: " + Diff.trim(Support.get(this)));
			}
		}

		default void hasSize(int expectedSize) {
			List<T> list = Support.getList(this);
			if (list.size() != expectedSize) {
				throw new AssertionError(
						"expected size: " + expectedSize,
						"actual size: " + list.size() + " — " + Diff.trim(Support.get(this)));
			}
		}

		default void has(T expectedElement) {
			List<T> list = Support.getList(this);
			if (!list.contains(expectedElement)) {
				throw new AssertionError(
						"expected element: " + expectedElement,
						"actual none — " + Diff.trim(Support.get(this)));
			}
		}

		default void isOf(java.lang.Iterable<T> expectedElements) {
			List<T> actualList = Support.getList(this);
			List<T> expectedList = new ArrayList<>();
			expectedElements.forEach(expectedList::add);

			if (!actualList.equals(expectedList)) {
				List<java.lang.String> diff = Diff.diff(
						Support.joinElement(expectedList),
						Support.joinElement(actualList));

				throw new AssertionError(
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
			List<T> actualElements = Support.getList(this);
			List<T> missingElements = new ArrayList<>();
			for (T e : expectedElements) {
				if (!actualElements.contains(e)) {
					missingElements.add(e);
				}
			}
			if (!missingElements.isEmpty()) {
				throw new AssertionError(
						"expected has all: " + Support.joinElement(expectedElements),
						"actual: missing " + Support.joinElement(missingElements) + " — " + Diff.trim(actualElements));
			}
		}

		default void hasOnly(@SuppressWarnings("unchecked") T... expectedElements) {
			hasOnly(Arrays.asList(expectedElements));
		}

		default void hasOnly(java.lang.Iterable<T> elements) {
			List<T> remainingElements = Support.getList(this);
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

			if (missingElements.isEmpty() && !remainingElements.isEmpty()) {
				java.lang.String actual = "actual: ";
				if (!missingElements.isEmpty()) {
					actual += "missing " + Support.joinElement(missingElements) + "; ";
				}
				if (!remainingElements.isEmpty()) {
					actual += "extra  " + Support.joinElement(remainingElements);
				}
				throw new AssertionError(
						"expected only: " + Support.joinElement(expectedElements),
						actual);
			}
		}
	}

	interface Double extends That<java.lang.Double, Double> {

		default void is(DoublePredicate predicate) {
			double actual = Support.get(this);
			if (!predicate.test(actual)) {
				throw new AssertionError("expected matching DoublePredicate", "actual: " + actual);
			}
		}

		default void not(DoublePredicate predicate) {
			double actual = Support.get(this);
			if (predicate.test(actual)) {
				throw new AssertionError("expected not matching DoublePredicate", "actual: " + actual);
			}
		}

		default void isWithinOf(double epsilon, double expected) {
			double actual = Support.get(this);
			if (Math.abs(actual - expected) > epsilon) {
				throw new AssertionError("expected within " + expected + " ±" + epsilon + "", "actual: " + actual);
			}
		}

		default void isBitwise(double expected) {
			double actual = Support.get(this);
			if (java.lang.Double.doubleToRawLongBits(actual) != java.lang.Double.doubleToRawLongBits(expected)) {
				throw new AssertionError("expected: " + expected, "actual: " + actual);
			}
		}
	}

	interface Int extends That<java.lang.Integer, Int> {
		default void is(IntPredicate predicate) {
			int actual = Support.get(this);
			if (!predicate.test(actual)) {
				throw new AssertionError("expected matching IntPredicate", "actual: " + actual);
			}
		}

		default void not(IntPredicate predicate) {
			int actual = Support.get(this);
			if (predicate.test(actual)) {
				throw new AssertionError("expected not matching IntPredicate", "actual: " + actual);
			}
		}

		default void is(int expected) {
			int actual = Support.get(this);
			if (actual != expected) {
				throw new AssertionError("expected: " + expected, "actual: " + actual);
			}
		}
	}

	interface Long extends That<java.lang.Long, Long> {
		default void is(LongPredicate predicate) {
			long actual = Support.get(this);
			if (!predicate.test(actual)) {
				throw new AssertionError("expected matching LongPredicate", "actual: " + actual);
			}
		}

		default void not(LongPredicate predicate) {
			long actual = Support.get(this);
			if (predicate.test(actual)) {
				throw new AssertionError("expected not matching LongPredicate", "actual: " + actual);
			}
		}

		default void is(long expected) {
			long actual = Support.get(this);
			if (actual != expected) {
				throw new AssertionError("expected: " + expected, "actual: " + actual);
			}
		}
	}

	public class Support<T, S extends That<T, S>> implements That<T, S> {
		private T value;

		@SuppressWarnings("unchecked")
		S set(@Nullable T value) {
			this.value = value;
			return (S) this;
		}

		T getNullable() {
			return value;
		}

		T get() {
			if (value == null) {
				throw new AssertionError("non-null expected");
			}
			return value;
		}

		/**
		 * @deprecated Do not use equals
		 */
		@Deprecated
		@Override
		public boolean equals(java.lang.Object obj) {
			throw new UnsupportedOperationException();
		}

		@Deprecated
		@Override
		public int hashCode() {
			throw new UnsupportedOperationException();
		}

		static <T, S extends That<T, S>> Support<T, S> base(That<T, S> that) {
			return (Support<T, S>) that;
		}

		static <T, S extends That<T, S>> T get(That<T, S> that) {
			return base(that).get();
		}

		static <T, S extends That<java.lang.Iterable<T>, S>> List<T> getList(That<java.lang.Iterable<T>, S> that) {
			java.lang.Iterable<T> actual = base(that).get();
			List<T> list = new ArrayList<>();
			actual.forEach(list::add);
			return list;
		}

		static java.lang.String joinElement(java.lang.Iterable<?> iterable) {
			return StreamSupport.stream(iterable.spliterator(), false)
					.map(Objects::toString)
					.collect(Collectors.joining(", "));
		}

		static <T, S extends That<T, S>> T getNullable(That<T, S> that) {
			return base(that).getNullable();
		}

		static java.lang.String printReference(java.lang.Object ref) {
			return ref == null ? "null" : (ref.getClass().getName() + "@" + System.identityHashCode(ref));
		}

		static java.lang.String printToStringDetail(java.lang.Object ref) {
			return ref == null ? "" : (" — " + ref);
		}
	}
}
