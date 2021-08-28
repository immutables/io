package io.immutables.micro.kafka;

import io.immutables.codec.OkJson;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.MicroInfo;
import io.immutables.micro.Servicelet;
import io.immutables.micro.Streams;
import io.immutables.micro.wiring.docker.DockerRunner;
import io.immutables.stream.Keyed;
import io.immutables.stream.Receiver;
import io.immutables.stream.Topic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
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
import static io.immutables.micro.kafka.Await.await;

@Ignore("Have no idea whats happening here")
public class DispatcherWithOnePartitionTest {

  private static final OkJson json = new OkJson();

  private static KafkaModule.BrokerInfo brokerInfo;
  private static final Servicelet.Name servicelet = Servicelet.name("test");

  private ListMultimap<String, FooRecord> buckets;
  private Receiver<FooRecord> receiver;
  private final List<Throwable> errors = new ArrayList<>();
  private final ExceptionSink sink = ExceptionSink.collectingUnhandled(errors::add);

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
  public void before() {
    topic = Topic.of(topicPrefix.incrementAndGet() + "-test-1p-topic-" + getClass().getSimpleName());
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

    KafkaAdmin.create(brokerInfo.connect(), topic, 1);

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
  public void shouldProcessOneRecord() {
    dispatcher1 = createAndStartDispatcher("Group_10", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record = new FooRecord("foo 1");
    send(record);

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 1);
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");

    that(buckets.values()).hasOnly(record);

    dispatcher1.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(1);
  }

  @Test
  public void shouldProcessOneHundredRecords() {
    dispatcher1 = createAndStartDispatcher("Group_20", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    List<FooRecord> hundredRecords = IntStream.range(1, 101).mapToObj(i -> new FooRecord("foo " + i)).collect(toList());
    hundredRecords.forEach(this::send);

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 100);
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(hundredRecords);

    dispatcher1.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(100);
  }

  @Test
  public void shouldPauseConsumingNewRecordsUntilProcessedCurrentAndThenResume() {
    dispatcher1 = createAndStartDispatcher("Group_30", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record1 = new FooRecord("foo 1");
    FooRecord record2 = new FooRecord("foo 2");
    FooRecord record3 = new FooRecord("foo 3");

    Sleep sleep = new Sleep(record1, 2000);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(record1, record2, record3);

    Await.await(800, MILLIS).doWait();
    // due to first record long processing we stop consuming
    // at this moment nothing should be here
    that(buckets.keySet()).isEmpty();
    that(buckets.values()).isEmpty();

    Await.await(10, SECONDS).until(() -> buckets.values().size() == 3);
    // after first record processed we resume consuming rest or records
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record1, record2, record3);

    dispatcher1.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(3);
  }

  @Test
  public void shouldProcessRecordOnlyOneTimeWhileReCreateDispatcherInTheMiddleOfProcessing() {
    dispatcher1 = createAndStartDispatcher("Group_40", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record1 = new FooRecord("foo 1");

    Sleep sleep = new Sleep(record1, 1000);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(record1);

    Await.await(800, MILLIS).doWait();
    // due to record long processing at this moment nothing should be here
    that(buckets.keySet()).isEmpty();
    that(buckets.values()).isEmpty();

    // close(any rebalance/fail/recreate actions) consumer
    dispatcher1.stopAsync().awaitTerminated();
    // dispatcher should finish current records before shut down
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record1);

    dispatcher2 = createAndStartDispatcher("Group_40", true);
    Await.await(10, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(500, MILLIS).doWait();
    // verify no new records processed twice
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record1);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(1);
  }

  @Test
  public void shouldProcessRecordOnlyOneTimeWhileReAssignDispatcherInTheMiddleOfProcessing() {
    dispatcher1 = createAndStartDispatcher("Group_50", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    dispatcher2 = createAndStartDispatcher("Group_50", true);

    FooRecord record = new FooRecord("foo 1");

    Sleep sleep = new Sleep(record, 1000);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(record);

    Await.await(800, MILLIS).doWait();
    // due to record long processing at this moment nothing should be here
    that(buckets.keySet()).isEmpty();
    that(buckets.values()).isEmpty();

    // close(any rebalance/fail/recreate actions) consumer
    dispatcher1.stopAsync().awaitTerminated();
    // dispatcher should finish current records before shut down
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);
    Await.await(5, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(1500, MILLIS).doWait();
    // verify no new records processed twice
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(1);
  }

  @Test
  public void shouldProcessRecordTwiceWhileReAssignDispatcherInTheMiddleOfProcessingWithoutCommit() {
    dispatcher1 = createAndStartDispatcher("Group_60", false);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    dispatcher2 = createAndStartDispatcher("Group_60", false);

    FooRecord record = new FooRecord("foo 1");

    Sleep sleep = new Sleep(record, 1000);
    receiver = records -> records.forEach(r -> {
      sleep.sleepIfMatch(r);
      buckets.put(Thread.currentThread().getName(), r);
    });

    send(record);

    Await.await(800, MILLIS).doWait();
    // due to record long processing at this moment nothing should be here
    that(buckets.keySet()).isEmpty();
    that(buckets.values()).isEmpty();

    // close(any rebalance/fail/recreate actions) consumer
    dispatcher1.stopAsync().awaitTerminated();
    // dispatcher1 should finish current records before shut down
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);
    Await.await(5, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 2);
    // verify records processed twice
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher2.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record, record);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(2);
  }

  @Test
  public void shouldReCreateNewPartitionHandlerForFailedRecord() {
    dispatcher1 = createAndStartDispatcher("Group_70", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record = new FooRecord("foo 1");

    OneTimeError oneTimeError = new OneTimeError(record);
    receiver = records -> records.forEach(r -> {
      //  on first iteration, wont throw on second iteration
      // thread id is added so we can check later that handler is actually recreated
      oneTimeError.throwIfMatch(r,
          new RuntimeException(Thread.currentThread().getName() + "/" + Thread.currentThread().getId()));
      buckets.put(Thread.currentThread().getName() + "/" + Thread.currentThread().getId(), r);
    });

    send(record);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 1);
    // first handler should fail due to poison record
    // than a new handler should be created for success processing
    that(errors).hasSize(1);
    that(buckets.keySet()).hasSize(1);
    that(buckets.values()).hasOnly(record);

    String threadBefore = errors.get(0).getCause().getMessage();
    String threadAfter = buckets.keySet().iterator().next();
    that(threadBefore).startsWith(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(threadAfter).startsWith(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(threadBefore.equals(threadAfter)).is(false);

    dispatcher1.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(1);
  }

  @Test
  public void shouldProcessOneRecordOnlyOneTimeWithAutoCommitOn() {
    dispatcher1 = createAndStartDispatcher("Group_80", true);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record = new FooRecord("foo 1");
    send(record);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 1);
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);

    // close(any rebalance/fail/recreate actions) consumer and then create a new one
    dispatcher1.stopAsync().awaitTerminated();
    dispatcher2 = createAndStartDispatcher("Group_80", true);
    Await.await(5, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(500, MILLIS).doWait();//to check that no records are processed twice
    // same record should be processed only one time as we have committed offset(auto commit was on)
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);
    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(1);
  }

  @Test
  public void shouldProcessOneRecordOnlyOneTimeWithManualCommit() {
    dispatcher1 = createAndStartDispatcher("Group_90", false);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    //process records with manual commit
    receiver = records -> {
      buckets.putAll(Thread.currentThread().getName(), records);
      records.commit();
    };

    FooRecord record = new FooRecord("foo 1");
    send(record);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 1);
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);

    // close(any rebalance/fail/recreate actions) consumer and then create a new one
    dispatcher1.stopAsync().awaitTerminated();
    dispatcher2 = createAndStartDispatcher("Group_90", false);
    Await.await(5, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(500, MILLIS).doWait();//to check that no records are processed twice
    // same record should be processed only one time as we have committed offset(manually committed)
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(1);
  }

  @Test
  public void shouldProcessFirstRecordOnlyOneTimeWithManualCommitButSecondTwice() {
    FooRecord record1 = new FooRecord("foo 1");
    FooRecord record2 = new FooRecord("foo 2");

    dispatcher1 = createAndStartDispatcher("Group_100", false);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    //process first records with manual commit but second records no commit
    receiver = records -> {
      buckets.putAll(Thread.currentThread().getName(), records);
      if (Iterables.contains(records, record1)) {
        records.commit();
      }
    };

    send(record1);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 1);
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record1);

    send(record2);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 2);
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record1, record2);

    // close(any rebalance/fail/recreate actions) consumer and then create a new one
    dispatcher1.stopAsync().awaitTerminated();
    dispatcher2 = createAndStartDispatcher("Group_100", false);
    Await.await(5, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 3);
    // first record should be processed only one time as we have committed offset(manually committed)
    // but second twice due to missed committed offset(no manual commit and auto commit is off)
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher2.serviceName() + ":Handler<shard-0>"
    );
    that(buckets.values()).hasOnly(record1, record2, record2);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(3);
  }

  @Test
  public void shouldProcessOneRecordTwiceIfNoCommit() {
    dispatcher1 = createAndStartDispatcher("Group_110", false);
    Await.await(5, SECONDS).until(dispatcher1::connectedToPartitions);

    FooRecord record = new FooRecord("foo 1");
    send(record);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 1);
    that(buckets.keySet()).hasOnly(dispatcher1.serviceName() + ":Handler<shard-0>");
    that(buckets.values()).hasOnly(record);

    // close(any rebalance/fail/recreate actions) consumer and then create a new one
    dispatcher1.stopAsync().awaitTerminated();
    dispatcher2 = createAndStartDispatcher("Group_110", false);
    Await.await(5, SECONDS).until(dispatcher2::connectedToPartitions);

    Await.await(5, SECONDS).until(() -> buckets.values().size() == 2);
    // same record should be processed twice due to missed committed offset(no manual commit and auto commit is off)
    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher2.serviceName() + ":Handler<shard-0>"
    );
    that(buckets.values()).hasOnly(record, record);

    dispatcher2.stopAsync().awaitTerminated();
    that(buckets.values()).hasSize(2);
  }

  private class OneTimeError {

    private int count = 0;
    private FooRecord target;

    private OneTimeError(FooRecord target) {
      this.target = target;
    }

    private void throwIfMatch(FooRecord record, RuntimeException e) {
      if (target.equals(record)) {
        if (count == 0) {
          count++;
          throw e;
        }
      }
    }
  }

  // FIXME bogus abstraction remove it
  private class Sleep {

    private FooRecord target;
    private long millis;

    private Sleep(FooRecord target, long millis) {
      this.target = target;
      this.millis = millis;
    }

    private void sleepIfMatch(FooRecord record) {
      if (target.equals(record)) {
        try {
          Thread.sleep(millis);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private void send(FooRecord... record) {
    try (KafkaSender<FooRecord> sender = new KafkaSender<>(servicelet, brokerInfo, json, sink, senderSetup)) {
      sender.write(asList(record));
    }
  }

  private Dispatcher<FooRecord> createAndStartDispatcher(String group, boolean autoCommit) {
    Dispatcher<FooRecord> recordDispatcher = new Dispatcher<>(servicelet, brokerInfo, sink, json, () -> receiver,
        defaultSetupBuilder.group(group).autoCommit(autoCommit).build());
    recordDispatcher.startAsync().awaitRunning();
    return recordDispatcher;
  }

  public static class FooRecord implements Keyed<Long> {
    public String value;

    public FooRecord(String value) {
      this.value = value;
    }

    @Override public Long key() {
      return (long) value.hashCode();
    }

    @Override public String toString() {
      return toStringHelper(this)
          .add("key", key())
          .add("value", value)
          .toString();
    }

    @Override public boolean equals(Object o) {
      return o instanceof FooRecord && Objects.equal(value, ((FooRecord) o).value);
    }

    @Override public int hashCode() {
      return Objects.hashCode(value);
    }
  }
}
