package io.immutables.micro.kafka;

import io.immutables.codec.OkJson;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.MicroInfo;
import io.immutables.micro.Servicelet;
import io.immutables.micro.Streams;
import io.immutables.micro.wiring.docker.DockerRunner;
import io.immutables.stream.Keyed;
import io.immutables.stream.Receiver;
import io.immutables.stream.Sharded;
import io.immutables.stream.Topic;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.net.HostAndPort;
import org.junit.*;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.net.HostAndPort.fromParts;
import static io.immutables.that.Assert.that;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static io.immutables.micro.kafka.Await.await;

@Ignore("Hope temporary")
public class DispatcherWithTwoPartitionsTest {

  private static KafkaModule.BrokerInfo brokerInfo;
  private static final Servicelet.Name servicelet = Servicelet.name("test");
  private static final OkJson JSON = new OkJson();
  private static final ExceptionSink SINK = ExceptionSink.assertNoUnhandled();

  private ListMultimap<String, FooRecord> buckets;
  private Receiver<FooRecord> receiver;

  private Topic topic;
  private static final AtomicInteger topicPrefix = new AtomicInteger(0);

  private KafkaSender.Setup senderSetup;
  private Dispatcher.Setup.Builder defaultSetupBuilder;

  private Dispatcher<FooRecord> dispatcher1;
  private Dispatcher<FooRecord> dispatcher2;

  @BeforeClass
  public static void ensureListening() {
    String kafkaHost = DockerRunner.assertKafkaIsRunning(KafkaModule.KAFKA_STANDARD_PORT);
    brokerInfo = new KafkaModule.BrokerInfo.Builder()
        .hostPort(fromParts(kafkaHost, KafkaModule.KAFKA_STANDARD_PORT))
        .setup(new Streams.Setup.Builder().build())
        .build();
  }

  @Before
  public void init() {
    topic = Topic.of(topicPrefix.incrementAndGet() + "-test-2p-topic-" + getClass().getSimpleName());
    senderSetup = new KafkaSender.Setup.Builder()
        .topic(topic)
        .type(FooRecord.class)
        .build();

    defaultSetupBuilder = new Dispatcher.Setup.Builder()
        .topic(topic)
        .type(FooRecord.class)
        .pollInterval(Duration.ofMillis(20))
        .idleReceiverTimeout(Duration.ofMinutes(5))
        .maxPollRecords(10);
    KafkaAdmin.create(brokerInfo.connect(), topic, 2);

    buckets = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    receiver = records -> buckets.putAll(Thread.currentThread().getName(), records);
  }

  @After
  public void after() {
    if (dispatcher1 != null) dispatcher1.stopAsync().awaitTerminated();
    if (dispatcher2 != null) dispatcher2.stopAsync().awaitTerminated();
    KafkaAdmin.deleteTopic(brokerInfo.connect(), topic);
  }

  @Test
  public void shouldProcessRecordPerHandler() {
    dispatcher1 = createAndStartDispatcher("Group_10", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record1 = createRecord("foo 1", 0);
    FooRecord record2 = createRecord("foo 2", 1);
    send(record1);
    send(record2);

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 2);
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>");
    that(buckets.values()).hasOnly(record1, record2);

    dispatcher1.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(2);
  }

  @Test
  public void shouldProcessOneHundredRecords() {
    dispatcher1 = createAndStartDispatcher("Group_20", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    List<FooRecord> hundredRecords = range(1, 101).mapToObj(i -> createRecord("foo " + i, i % 2)).collect(toList());
    send(hundredRecords);

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 100);
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>");
    that(buckets.values()).hasSize(100);
    that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-0>")).hasSize(50);
    that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-1>")).hasSize(50);
    that(buckets.values()).hasOnly(hundredRecords);

    dispatcher1.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(100);
  }

  @Test
  public void shouldPauseConsumingNewRecordsUntilProcessedCurrentAndThenResume() {
    dispatcher1 = createAndStartDispatcher("Group_30", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record1 = createRecord("foo 1", 0);
    FooRecord record2 = createRecord("foo 2", 1);
    FooRecord record3 = createRecord("foo 3", 0);
    FooRecord record4 = createRecord("foo 4", 1);

    Sleep sleep = new Sleep(1500, record1);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(asList(record1, record2, record3, record4));

    Await.await(1, SECONDS).until(() -> buckets.values().size() == 2);
    // due to some record long processing we stop consuming for partition 0
    // at this moment here should be records from another partition only
    that(buckets.values()).hasOnly(record2, record4);

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 4);
    // after first record processed we resume consuming rest or records
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>");
    that(buckets.values()).hasOnly(record1, record2, record3, record4);

    dispatcher1.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(4);
  }

  @Test
  public void shouldProcessRecordOnlyOneTimeWhileReCreateDispatcherInTheMiddleOfProcessing() {
    dispatcher1 = createAndStartDispatcher("Group_40", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record1 = createRecord("foo 1", 0);
    FooRecord record2 = createRecord("foo 2", 1);

    Sleep sleep = new Sleep(2000, record1);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(record1);//records are sharded so will fall into different partitions
    send(record2);

    Await.await(1, SECONDS).doWait();
    // at this moment due to record long processing on one partition
    // only one record from another partition should be here
    that(buckets.values()).hasOnly(record2);

    // close(any rebalance/fail/recreate actions) consumer
    dispatcher1.stopAsync().awaitTerminated();
    // dispatcher should finish(commit offsets) current records before shut down
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>");
    that(buckets.values()).hasOnly(record1, record2);

    dispatcher2 = createAndStartDispatcher("Group_40", true);
    Await.await(5, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(2, SECONDS).doWait();
    // verify no new records processed twice
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>");
    that(buckets.values()).hasOnly(record1, record2);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(2);
  }

  @Test
  public void shouldProcessRecordOnlyOneTimeWhileReBalanceDispatcherInTheMiddleOfProcessing() {
    dispatcher1 = createAndStartDispatcher("Group_50", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record1 = createRecord("foo 1", 0);
    FooRecord record2 = createRecord("foo 2", 1);
    FooRecord record3 = createRecord("foo 3", 0);
    FooRecord record4 = createRecord("foo 4", 1);
    FooRecord record5 = createRecord("foo 5", 0);
    FooRecord record6 = createRecord("foo 6", 1);

    Sleep sleep = new Sleep(4000, record1, record2);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(asList(record1, record2));
    Await.await(300, MILLIS).doWait();
    send(asList(record3, record4, record5, record6));
    Await.await(500, MILLIS).doWait();
    // at this moment due to records long processing on partition handlers 0 and 1
    // nothing should be here
    that(buckets.keySet()).isEmpty();
    that(buckets.values()).isOf();

    //second dispatcher will lead to rebalance and one partition(we don't know which one) will be reassigned to new one
    dispatcher2 = createAndStartDispatcher("Group_50", true);
    Await.await(10, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 6);
    // verify no new records processed twice
    that(buckets.values()).hasOnly(record1, record2, record3, record4, record5, record6);

    //after rebalance we don't know exact assignments so we will have two possible variants of assignments
    if (buckets.keySet().contains(dispatcher2.serviceName() + ":Handler<shard-0>")) {
      that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-0>")).hasOnly(record1);
      that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-1>")).hasOnly(record2, record4, record6);
      that(buckets.get(dispatcher2.serviceName() + ":Handler<shard-0>")).hasOnly(record3, record5);
      that(buckets.get(dispatcher2.serviceName() + ":Handler<shard-1>")).hasOnly();
    } else {
      that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-0>")).hasOnly(record1, record3, record5);
      that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-1>")).hasOnly(record2);
      that(buckets.get(dispatcher2.serviceName() + ":Handler<shard-0>")).hasOnly();
      that(buckets.get(dispatcher2.serviceName() + ":Handler<shard-1>")).hasOnly(record4, record6);
    }

    dispatcher1.stopAsync().awaitTerminated();
    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(6);
  }

  @Test
  public void shouldProcessRecordTwiceWhileReCreateDispatcherInTheMiddleOfProcessingWithoutCommit() {
    dispatcher1 = createAndStartDispatcher("Group_60", false);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record1 = createRecord("foo 1", 0);
    FooRecord record2 = createRecord("foo 2", 1);

    Sleep sleep = new Sleep(2000, record1);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(record1);
    send(record2);

    Await.await(1, SECONDS).doWait();
    // at this moment due to record long processing on one partition
    // only one record from another partition should be here
    that(buckets.values()).hasOnly(record2);

    // close(any rebalance/fail/recreate actions) consumer. then rebalance
    dispatcher1.stopAsync().awaitTerminated();
    // dispatcher1 should finish current records before shut down
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>");
    that(buckets.values()).hasOnly(record1, record2);

    dispatcher2 = createAndStartDispatcher("Group_60", false);

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 4);
    // verify records processed twice
    that(buckets.values()).hasOnly(record1, record1, record2, record2);
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>",
        dispatcher2.serviceName() + ":Handler<shard-0>",
        dispatcher2.serviceName() + ":Handler<shard-1>");

    that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-0>")).hasOnly(record1);
    that(buckets.get(dispatcher2.serviceName() + ":Handler<shard-0>")).hasOnly(record1);
    that(buckets.get(dispatcher1.serviceName() + ":Handler<shard-1>")).hasOnly(record2);
    that(buckets.get(dispatcher2.serviceName() + ":Handler<shard-1>")).hasOnly(record2);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(4);
  }

  private class Sleep {

    private List<FooRecord> targets;
    private long millis;

    private Sleep(long millis, FooRecord... target) {
      this.targets = asList(target);
      this.millis = millis;
    }

    private void sleepIfMatch(FooRecord record) {
      if (targets.contains(record)) {
        try {
          Thread.sleep(millis);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private void send(FooRecord record) {
    try (KafkaSender<FooRecord> sender = new KafkaSender<>(servicelet, brokerInfo, JSON, SINK, senderSetup)) {
      sender.write(record);
    }
  }

  private void send(List<FooRecord> records) {
    try (KafkaSender<FooRecord> sender = new KafkaSender<>(servicelet, brokerInfo, JSON, SINK, senderSetup)) {
      sender.write(records);
    }
  }

  private Dispatcher<FooRecord> createAndStartDispatcher(String group, boolean autoCommit) {
    Dispatcher<FooRecord> recordDispatcher = new Dispatcher<>(servicelet, brokerInfo, SINK, JSON, () -> receiver,
        defaultSetupBuilder.group(group).autoCommit(autoCommit).build());
    recordDispatcher.startAsync().awaitRunning();
    return recordDispatcher;
  }

  private class FooRecord implements Keyed<Integer>, Sharded<Integer> {
    private String value;
    private FooRecord(String value) {
      this.value = value;
    }

    @Override
    public Integer key() {
      return value.hashCode();
    }

    @Override
    public Integer shardKey() {
      return value.hashCode();
    }

    @Override public String toString() {
      return toStringHelper(this)
          .add("key", key())
          .add("value", value)
          .toString();
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FooRecord)) return false;
      FooRecord record = (FooRecord) o;
      return Objects.equal(value, record.value);
    }

    @Override public int hashCode() {
      return Objects.hashCode(value);
    }
  }

  private FooRecord createRecord(final String value, final int shard) {
    return new FooRecord(value) {
      @Override public Integer shardKey() {
        return shard;
      }
    };
  }
}
