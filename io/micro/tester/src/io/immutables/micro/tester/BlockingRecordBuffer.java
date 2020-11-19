package io.immutables.micro.tester;

import io.immutables.stream.Receiver;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import com.google.common.annotations.VisibleForTesting;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class BlockingRecordBuffer<R> implements RecordBuffer<R>, Receiver<R> {
  @VisibleForTesting final BlockingQueue<R> queue = new LinkedBlockingDeque<>();
  @VisibleForTesting final List<R> records = new CopyOnWriteArrayList<>();

  private final long defaultTimeout;
  private final TimeUnit defaultUnit;

  private long timeout;
  private TimeUnit unit;
  private boolean giveupSilently;
  private Predicate<R> filter = r -> true;

  BlockingRecordBuffer(long defaultTimeout, TimeUnit defaultUnit) {
    this.defaultUnit = defaultUnit;
    this.defaultTimeout = defaultTimeout;
    setupTimeout(defaultTimeout, defaultUnit, false);
  }

  private void setupTimeout(long timeout, TimeUnit unit, boolean giveupSilently) {
    checkArgument(timeout > 0, "Timeout has to be greater than 0");
    this.timeout = timeout;
    this.unit = unit;
    this.giveupSilently = giveupSilently;
  }

  @Override
  public void reset() {
    queue.clear();
    records.clear();
    setupTimeout(defaultTimeout, defaultUnit, false);
    filter = r -> true;
  }

  @Override
  public void on(Records<R> records) {
    records.forEach(queue::add);
  }

  @Override
  public RecordBuffer<R> filter(Predicate<R> filter) {
    this.filter = filter;
    return this;
  }

  @Override
  public RecordBuffer<R> timeoutAfter(long timeout, TimeUnit unit) {
    setupTimeout(timeout, unit, false);
    return this;
  }

  @Override
  public RecordBuffer<R> giveupAfter(long timeout, TimeUnit unit) {
    setupTimeout(timeout, unit, true);
    return this;
  }

  @Override
  public List<R> collect(Predicate<List<R>> takesWhileTrue, Predicate<R> stopsWhenTrue, boolean takeSomeMoreIfAvailable) {
    long remainingTimeout = unit.toNanos(timeout);
    while (takesWhileTrue.test(records) && remainingTimeout > 0) {
      long nanoStart = System.nanoTime();
      R record;
      try {
        record = queue.poll(remainingTimeout, NANOSECONDS);
      } catch (InterruptedException e) {
        throw new AssertionError("Failed to collect records due to the thread interruption");
      }
      if (record != null && filter.test(record)) {
        if (stopsWhenTrue.test(record)) {
          remainingTimeout -= System.nanoTime() - nanoStart;
          break;
        }
        records.add(record);
      }
      remainingTimeout -= System.nanoTime() - nanoStart;
    }
    if (remainingTimeout > 0 || giveupSilently) {
      if (takeSomeMoreIfAvailable) {
        R record;
        while ((record = queue.poll()) != null) {
          records.add(record);
        };
      }
      return records;
    }

    throw new AssertionError("Failed to collect required records in time");
  }
}
