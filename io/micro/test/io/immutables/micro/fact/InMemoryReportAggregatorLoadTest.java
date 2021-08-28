package io.immutables.micro.fact;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import io.immutables.codec.OkJson;
import io.immutables.micro.CodecsFactory;
import io.immutables.micro.ExceptionSink;
import org.junit.Ignore;
import org.junit.Test;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static com.google.common.collect.Range.closed;
import static io.immutables.micro.fact.ReportEntry.Kind.D;
import static io.immutables.micro.fact.ReportEntry.Kind.V;
import static io.immutables.that.Assert.that;

@Ignore
public class InMemoryReportAggregatorLoadTest {
  static final OkJson coder = OkJson.configure(c -> c.add(new CodecsFactory()));
  static final ExceptionSink sink = ExceptionSink.assertNoUnhandled();
  final InMemoryReportAggregator aggregator = new InMemoryReportAggregator(sink,coder);

  @Test
  public void test() throws ExecutionException, InterruptedException {
    var executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));

    // add some parents
    executor.submit(new Writer(10, 0, closed(1, 5))).get();

    // load
    var list = ImmutableList.<ListenableFuture<?>>builder()
        .add(executor.submit(new RandomReader(200, 100, closed(1, 5))))
        .add(executor.submit(new RandomReader(30, 300, closed(100, 599))))
        .add(executor.submit(new Writer(100, 1, closed(100, 199))))
        .add(executor.submit(new Writer(100, 2, closed(200, 299))))
        .add(executor.submit(new RandomReader(20, 200, closed(100, 599))))
        .add(executor.submit(new Writer(100, 3, closed(300, 399))))
        .add(executor.submit(new Writer(100, 4, closed(400, 499))))
        .add(executor.submit(new Discontinuer(3)))
        .add(executor.submit(new RandomReader(10, 100, closed(100, 599))))
        .add(executor.submit(new Writer(100, 5, closed(500, 599))))
        .build();

    Futures.allAsList(list).get();

    // final check
    that(aggregator.size()).is(405);
  }

  class Writer implements Runnable {
    final int delay;
    final int parentId;
    final ContiguousSet<Integer> idRange;

    Writer(int delay, int parentId, Range<Integer> idRange) {
      this.delay = delay;
      this.parentId = parentId;
      this.idRange = ContiguousSet.create(idRange, DiscreteDomain.integers());
    }

    @Override public void run() {
      idRange.forEach(id -> {
        aggregator.report(ReportEntry.of(id, parentId, V, "", parentId + ":" + id, Instant.now()));
        Uninterruptibles.sleepUninterruptibly(delay, TimeUnit.MILLISECONDS);
      });
    }
  }

  class RandomReader implements Runnable {
    final int delay;
    final int count;
    final ContiguousSet<Integer> idRange;
    final Random random = new Random();

    RandomReader(int delay, int count, Range<Integer> idRange) {
      this.delay = delay;
      this.count = count;
      this.idRange = ContiguousSet.create(idRange, DiscreteDomain.integers());
    }

    @Override public void run() {
      IntStream.rangeClosed(0, count).forEach(count -> {
        aggregator.get(Iterables.get(idRange, random.nextInt(idRange.size())));
        Uninterruptibles.sleepUninterruptibly(delay, TimeUnit.MILLISECONDS);
      });
    }
  }

  class Discontinuer implements Runnable {
    final int id;

    Discontinuer(int id) {
      this.id = id;
    }

    @Override public void run() {
      aggregator.report(ReportEntry.of(id, 0, D, "", id, Instant.now()));
    }
  }
}
