package io.immutables.micro.creek;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import io.immutables.codec.OkJson;
import io.immutables.stream.Topic;
import static io.immutables.that.Assert.that;

public class DispatcherTest {
  static final Topic A = Topic.of("A");

  final OkJson json = new OkJson();

  final Broker broker = new Broker(() -> 0, 1, 1);
  {
    broker.create(A, 2);
  }
  final Dispatcher.Conf conf = new Dispatcher.Conf.Builder()
      .topic(Topic.of("A"))
      .type(String.class)
      .pollInterval(Duration.ofMillis(3))
      .build();

  @Test
  public void dispatchesShardsInSeparateThreads() throws InterruptedException {
    ListMultimap<Thread, String> receivedByThread =
        Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    CountDownLatch latch = new CountDownLatch(7);

    Dispatcher<String> dispatcher = new Dispatcher<>(
        broker,
        json,
        () -> records -> {
          receivedByThread.putAll(Thread.currentThread(), records);
          records.forEach(r -> latch.countDown());
        }, conf
    );

    dispatcher.startAsync().awaitRunning();

    publish(A,
        record(0, "a"),
        record(0, "b"),
        record(1, "c"));

    publish(A,
        record(0, "d"),
        record(1, "e"));

    publish(A,
        record(1, "f"),
        record(1, "g"));

    latch.await(5, TimeUnit.SECONDS);

    dispatcher.stopAsync().awaitTerminated();

    that(receivedByThread.keySet()).hasSize(2);

    receivedByThread.asMap().forEach((t, rs) -> {
      if (t.getName().equals("ShardHandler:A/0")) {
        that(rs).isOf("a", "b", "d");
      } else if (t.getName().equals("ShardHandler:A/1")) {
        that(rs).isOf("c", "e", "f", "g");
      } else that().unreachable(); // if this fails, check serviceName()/getName()
    });
  }

  private Broker.Record record(int shard, String value) {
    return broker.record(shard, null, json.toJson(value));
  }

  private void publish(Topic topic, Broker.Record... records) {
    broker.publish(topic).write(Arrays.asList(records));
  }
}
