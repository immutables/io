package io.immutables.micro.tester;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * RecordBuffer is useful for validating records that are exchanged using message brokers.
 * It is supposed that single RecordBuffer is subscribed to single topic.
 * @param <R> type of record
 */
public interface RecordBuffer<R> {
  /**
   * Specifies the predicate to filter collected records.
   */
  RecordBuffer<R> filter(Predicate<R> filter);

  /**
   * Specifies the timeout after which record collecting will be stopped and exception is thrown.
   */
  RecordBuffer<R> timeoutAfter(long timeout, TimeUnit unit);

  /**
   * Specifies the timeout after which record collecting will be stopped.
   * Unlike {@link #timeoutAfter(long, TimeUnit)} no exception will be thrown, collected records will be returned.
   */
  RecordBuffer<R> giveupAfter(long timeout, TimeUnit unit);

  /**
   * Resets buffer state. This method is useful with test hooks (e.g. before test).
   */
  void reset();

  /**
   * Records collected until predicate is returned {@code true} for a new record, which is not included.
   * {@link #filter(Predicate)} works before this test, so effectively it only works for unfiltered records
   */
  default List<R> takeUntil(Predicate<R> stopsWhenTrue) {
    return collect(rs -> true, stopsWhenTrue, false);
  }

  /**
   * Tries to take n records, then stop or giveup (silently or via timeout)
   */
  default List<R> take(int n) {
    return collect(records -> records.size() < n, r -> false, false);
  }

  /**
   * Records collected until predicate is returned 'false' first time.
   */
  default List<R> takeWhile(Predicate<List<R>> takesWhileTrue) {
    return collect(takesWhileTrue, r -> false, false);
  }

  List<R> collect(Predicate<List<R>> takesWhileTrue, Predicate<R> stopsWhenTrue, boolean takeSomeMoreIfAvailable);
}
