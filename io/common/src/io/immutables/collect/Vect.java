package io.immutables.collect;

import io.immutables.Capacity;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkPositionIndexes;
import static java.util.Objects.requireNonNull;

/**
 * Minimalistic wrapper around immutable array. We use it over ImmutableList because we want monomorphic call
 * sites, no unsupported mutation methods, minimum memory overhead, no views, have simplistic
 * pattern matching capability and short classname.
 * @param <E> element type
 */
@Immutable
@SuppressWarnings("unchecked")
public final class Vect<E> implements Iterable<E>, Foldable<E> {
	private static final Object[] EMPTY_ARRAY = new Object[] {};
	private static final Vect<?> EMPTY = new Vect<>(EMPTY_ARRAY);

	final Object[] elements;

	Vect(Object[] elements) {
		this.elements = elements;
	}

	@Override
	public Iterator iterator() {
		return new Iterator();
	}

	public <R> Vect<R> map(Function<E, R> to) {
		if (this == EMPTY) return of();
		Object[] newElements = new Object[elements.length];
		for (int i = 0; i < elements.length; i++) {
			newElements[i] = requireNonNull(to.apply((E) elements[i]));
		}
		return new Vect<>(newElements);
	}

	public <R> Vect<R> flatMap(Function<E, ? extends Iterable<R>> to) {
		Builder<R> builder = new Builder<>(elements.length);
		for (Object e : elements) {
			for (R r : to.apply((E) e)) {
				builder.add(r);
			}
		}
		return builder.build();
	}

	public Optional<E> findFirst(Predicate<? super E> is) {
		for (Object o : elements) {
			E e = (E) o;
			if (is.test(e)) return Optional.of(e);
		}
		return Optional.empty();
	}

	public boolean any(Predicate<? super E> is) {
		for (Object e : elements) {
			if (is.test((E) e)) return true;
		}
		return false;
	}

	public boolean all(Predicate<? super E> is) {
		for (Object e : elements) {
			if (!is.test((E) e)) return false;
		}
		return true;
	}

	public boolean contains(E element) {
		for (Object e : elements) {
			if (e.equals(element)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void forEach(Consumer<? super E> consumer) {
		for (Object e : elements) {
			consumer.accept((E) e);
		}
	}

	// safe unchecked: runtime isInstance check
	public <T> Vect<T> only(Class<T> type) {
		return (Vect<T>) filter(type::isInstance);
	}

	public Vect<E> takeWhile(Predicate<? super E> predicate) {
		int taken = 0;
		for (taken = 0; taken < elements.length; taken++) {
			if (!predicate.test((E) elements[taken])) break;
		}
		if (taken == 0) return (Vect<E>) EMPTY;
		if (taken == elements.length) return this;
		return range(0, taken);
	}

	public Vect<E> dropWhile(Predicate<? super E> predicate) {
		int dropped = 0;
		for (dropped = 0; dropped < elements.length; dropped++) {
			if (!predicate.test((E) elements[dropped])) break;
		}
		if (dropped == 0) return this;
		if (dropped == elements.length) return (Vect<E>) EMPTY;
		return rangeFrom(dropped);
	}

	public <R> R bipartition(Predicate<? super E> predicate, BiFunction<Vect<E>, Vect<E>, R> receiver) {
		Builder<E> yes = new Builder<>(elements.length);
		Builder<E> no = new Builder<>(elements.length);
		for (E e : this) {
			(predicate.test(e) ? yes : no).add(e);
		}
		return receiver.apply(yes.build(), no.build());
	}

	public Vect<E> filter(Predicate<? super E> is) {
		if (this == EMPTY) return this;
		Object[] newElements = elements.clone();
		int size = 0;
		for (Object e : elements) {
			if (is.test((E) e)) {
				newElements[size++] = e;
			}
		}
		return size == 0 ? of() : new Vect<>(Arrays.copyOf(newElements, size));
	}

	@Override
	public <A> A fold(A left, BiFunction<A, E, A> folder) {
		A a = requireNonNull(left);
		for (Object e : elements) {
			a = requireNonNull(folder.apply(a, (E) e));
		}
		return a;
	}

	@Override
	public <A> A fold(BiFunction<E, A, A> reducer, A right) {
		A a = requireNonNull(right);
		for (int i = elements.length - 1; i >= 0; i--) {
			a = requireNonNull(reducer.apply((E) elements[i], a));
		}
		return a;
	}

	public Vect<E> prepend(E element) {
		if (this == EMPTY) return of(element);
		return new Builder<E>(elements.length + 1)
				.add(element)
				.addAll((E[]) elements)
				.build();
	}

	public Vect<E> append(E element) {
		if (this == EMPTY) return of(element);
		return new Builder<E>(elements.length + 1)
				.addAll((E[]) elements)
				.add(element)
				.build();
	}

	public Vect<E> sort() {
		if (elements.length <= 1) return this;
		Object[] sorted = elements.clone();
		Arrays.sort(sorted);
		return new Vect<>(sorted);
	}

	public Vect<E> sort(Comparator<? super E> comparator) {
		if (elements.length <= 1) return this;
		Object[] sorted = elements.clone();
		Arrays.sort((E[]) sorted, comparator);
		return new Vect<>(sorted);
	}

	public Vect<E> reverse() {
		if (this == EMPTY) return this;
		Object[] reversed = elements.clone();
		for (int i = 0, j = reversed.length - 1, mid = reversed.length / 2; i < mid; i++, j--) {
			Object t = reversed[i];
			reversed[i] = reversed[j];
			reversed[j] = t;
		}
		return new Vect<>(reversed);
	}

	@Override
	public E reduce(BiFunction<E, E, E> reducer) {
		if (elements.length == 0) {
			throw new NoSuchElementException();
		}
		E a = (E) elements[0];
		for (int i = 1; i < elements.length; i++) {
			a = requireNonNull(reducer.apply(a, (E) elements[i]));
		}
		return a;
	}

	public Vect<E> range(int from, int to) {
		checkPositionIndexes(from, to, elements.length);
		if (from == to) return (Vect<E>) EMPTY;
		return new Vect<>(Arrays.copyOfRange(elements, from, to));
	}

	public Vect<E> rangeFrom(int from) {
		checkPositionIndex(from, elements.length);
		if (this == EMPTY) return this;
		return new Vect<>(Arrays.copyOfRange(elements, from, elements.length));
	}

	public E first() {
		if (isEmpty()) throw new NoSuchElementException("first()");
		return (E) elements[0];
	}

	public E last() {
		if (isEmpty()) throw new NoSuchElementException("first()");
		return (E) elements[elements.length - 1];
	}

	public E get(int index) {
		return (E) elements[index];
	}

	public int size() {
		return elements.length;
	}

	public boolean isEmpty() {
		return elements.length == 0;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(elements);
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this
				|| (obj instanceof Vect<?>
						&& Arrays.equals(elements, ((Vect<?>) obj).elements));
	}

	@Override
	public Spliterator<E> spliterator() {
		return Spliterators.spliterator(elements, Spliterator.IMMUTABLE);
	}

	public Stream<E> stream() {
		return StreamSupport.stream(spliterator(), false);
	}

	@Override
	public String toString() {
		return join(", ", "[", "]");
	}

	public String join(String separator) {
		return join(separator, "", "");
	}

	public String join(String separator, String prefix, String suffix) {
		return stream().map(Object::toString).collect(Collectors.joining(separator, prefix, suffix));
	}

	public Vect<E> concat(Vect<? extends E> v) {
		return new Builder<E>(size() + v.size())
				.addAll(this)
				.addAll(v)
				.build();
	}

	public Object[] toArray() {
		return elements.clone();
	}

	public E[] toArray(E[] a) {
		if (a.length < elements.length) {
			return (E[]) Arrays.copyOf(elements, elements.length, a.getClass());
		}
		System.arraycopy(elements, 0, a, 0, elements.length);
		// by the strange (useful?) convention, we set next extra
		// element to null, but we don't null the rest if any.
		if (a.length > elements.length) a[elements.length] = null;
		return a;
	}

	public E[] toArray(IntFunction<E[]> factory) {
		return toArray(factory.apply(elements.length)); // some microbenchmarks recomment 0?
	}

	public static <E> Vect<E> of(E single) {
		requireNonNull(single);
		return new Vect<>(new Object[] {single});
	}

	public static <E> Vect<E> of(E first, E second) {
		return new Vect<>(new Object[] {requireNonNull(first), requireNonNull(second)});
	}

	@SafeVarargs
	public static <E> Vect<E> of(E... elements) {
		if (elements.length == 0) {
			return of();
		}
		E[] array = elements.clone();
		if (array.length == 0) {
			return of();
		}
		for (E e : array) {
			requireNonNull(e);
		}
		return new Vect<>(array);
	}

	public static <E> Vect<E> of() {
		return (Vect<E>) EMPTY;
	}

	@Deprecated
	public static <E> Vect<E> from(Vect<? extends E> vector) {
		return (Vect<E>) vector;
	}

	public static <E> Vect<E> from(Iterable<? extends E> iterable) {
		if (iterable instanceof Vect<?>) {
			return (Vect<E>) iterable;
		}
		if (iterable instanceof Collection<?>) {
			Object[] array = ((Collection<?>) iterable).toArray();
			if (array.length == 0) {
				return of();
			}
			return new Vect<>(array);
		}
		return Vect.<E>builder().addAll(iterable).build();
	}

	public static <E> Builder<E> builder() {
		return new Builder<>(10);
	}

	public static <E> Builder<E> builderWithExpectedSize(int size) {
		return new Builder<>(size);
	}

	public final class Iterator implements java.util.Iterator<E> {
		int index = 0;
		@Override
		public boolean hasNext() {
			return index < elements.length;
		}
		@Override
		public E next() {
			return (E) elements[index++];
		}
		public boolean wasLast() {
			return index == elements.length;
		}
		public boolean wasFirst() {
			return index == 1;
		}
		@Override
		public String toString() {
			return Vect.class.getSimpleName() + ".Iterator(at " + index + ")";
		}
	}

	@NotThreadSafe
	public static class Builder<E> {
		private Object[] elements = EMPTY_ARRAY;
		private int size;

		Builder(int expectedSize) {
			ensureCapacityFor(expectedSize);
		}

		Builder<E> combine(Builder<E> builder) {
			ensureCapacityFor(builder.size);
			System.arraycopy(builder.elements, 0, elements, size, builder.size);
			size += builder.size;
			return this;
		}

		@CheckReturnValue
		public Builder<E> add(E element) {
			requireNonNull(element);
			ensureCapacityFor(1);
			elements[size++] = element;
			return this;
		}

		@CheckReturnValue
		public Builder<E> addAll(E[] elements) {
			ensureCapacityFor(elements.length);
			for (Object e : elements) {
				// System array copy would be better, but we still
				// need to check elements for null
				this.elements[size++] = requireNonNull(e);
			}
			return this;
		}

		@CheckReturnValue
		public Builder<E> addAll(Iterable<? extends E> iterable) {
			requireNonNull(iterable);
			if (iterable instanceof Collection<?>) {
				Object[] array = ((Collection<?>) iterable).toArray();
				ensureCapacityFor(array.length);
				for (Object e : array) {
					elements[size++] = requireNonNull(e);
				}
			} else if (iterable instanceof Vect<?>) {
				Object[] array = ((Vect<?>) iterable).elements;
				ensureCapacityFor(array.length);
				for (Object e : array) {
					elements[size++] = requireNonNull(e);
				}
			} else {
				for (Object e : iterable) {
					ensureCapacityFor(1);
					elements[size++] = requireNonNull(e);
				}
			}
			return this;
		}

		private void ensureCapacityFor(int increment) {
			elements = Capacity.ensure(elements, size, increment);
		}

		public Vect<E> build() {
			return size == 0 ? of() : new Vect<>(Arrays.copyOf(elements, size));
		}
	}

	public <R> When<R> when() {
		return new When<>();
	}

	@NotThreadSafe
	public final class When<R> implements Supplier<R> {
		private @Nullable R result;

		@CheckReturnValue
		public When<R> empty(Supplier<R> onEmpty) {
			if (result == null && elements.length == 0) {
				result = requireNonNull(onEmpty.get());
			}
			return this;
		}

		@CheckReturnValue
		public When<R> single(Function<E, R> onSingle) {
			if (result == null && elements.length == 1) {
				result = requireNonNull(onSingle.apply((E) elements[0]));
			}
			return this;
		}

		@CheckReturnValue
		public When<R> pair(BiFunction<E, E, R> onPair) {
			if (result == null && elements.length == 2) {
				result = requireNonNull(onPair.apply((E) elements[0], (E) elements[1]));
			}
			return this;
		}

		@CheckReturnValue
		public When<R> head(BiFunction<E, Vect<E>, R> onHead) {
			if (result == null && elements.length >= 1) {
				E head = (E) elements[0];
				E[] tail = (E[]) Arrays.copyOfRange(elements, 1, elements.length);
				result = requireNonNull(onHead.apply(head, new Vect<>(tail)));
			}
			return this;
		}

		@CheckReturnValue
		public R otherwise(Function<Vect<E>, R> onElse) {
			if (result == null) {
				result = requireNonNull(onElse.apply(Vect.this));
			}
			return result;
		}

		@CheckReturnValue
		public R otherwise(R r) {
			if (result == null) {
				result = requireNonNull(r);
			}
			return result;
		}

		@Override
		public R get() {
			if (result == null) throw new IllegalStateException("Non exhaustive match, use 'otherwise' case");
			return result;
		}
	}

	public static <E> Collector<E, Vect.Builder<E>, Vect<E>> to() {
		return Collector.of(
				Vect::builder,
				Builder::add,
				Builder::combine,
				Builder::build);
	}
}
