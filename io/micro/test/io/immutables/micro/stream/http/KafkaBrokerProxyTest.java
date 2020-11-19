package io.immutables.micro.stream.http;

import io.immutables.codec.OkJson;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Streams;
import io.immutables.micro.kafka.Await;
import io.immutables.micro.kafka.KafkaAdmin;
import io.immutables.micro.kafka.KafkaModule;
import io.immutables.micro.stream.http.kafka.KafkaBroker;
import io.immutables.micro.stream.http.kafka.KafkaHttpModule;
import io.immutables.micro.wiring.docker.DockerRunner;
import io.immutables.stream.Keyed;
import io.immutables.stream.Receiver;
import io.immutables.stream.Sharded;
import io.immutables.stream.Topic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.google.common.base.Objects;
import com.google.common.collect.ListMultimap;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.collect.ArrayListMultimap.create;
import static com.google.common.collect.Multimaps.synchronizedListMultimap;
import static com.google.common.net.HostAndPort.fromParts;
import static io.immutables.that.Assert.that;
import static java.lang.Thread.currentThread;
import static java.time.temporal.ChronoUnit.SECONDS;
import static io.immutables.micro.kafka.Await.await;

@Ignore("Hope temporary")
public class KafkaBrokerProxyTest {

  private static BrokerApi broker;
  private static KafkaHttpModule.BrokerInfo brokenInfo;
  private static final OkJson json = new OkJson();
  private static Character topicPrefix = 'A';

  private List<FooRecord> list;
  private Topic topic;
  private Dispatcher<FooRecord> dispatcher1;
  private Dispatcher<FooRecord> dispatcher2;

  @Before
  public void init() {
    String kafkaHost = DockerRunner.assertKafkaIsRunning(KafkaModule.KAFKA_STANDARD_PORT);
    brokenInfo = new KafkaHttpModule.BrokerInfo.Builder()
        .hostPort(fromParts(kafkaHost, KafkaModule.KAFKA_STANDARD_PORT))
        .setup(new Streams.Setup.Builder().build())
        .build();
    broker = new KafkaBroker(brokenInfo, new KafkaBroker.Setup.Builder().build(), ExceptionSink.assertNoUnhandled());

    list = new CopyOnWriteArrayList<>();
    topic = Topic.of(topicPrefix++ + "-test-2p-topic-" + getClass().getSimpleName());
    KafkaAdmin.createIfNotExists(brokenInfo.connect(), topic, 2);
  }

  @After
  public void after() {
    if (dispatcher1 != null) dispatcher1.stopAsync().awaitTerminated();
    if (dispatcher2 != null) dispatcher2.stopAsync().awaitTerminated();
    ((KafkaBroker) broker).close();
    KafkaAdmin.deleteTopic(brokenInfo.connect(), topic);
  }

  @Test
  public void shouldProcessRecordOnlyOneTimeWhileReBalanceDispatcherInTheMiddleOfProcessing() {
    Producer<FooRecord> producer = new Producer<>(broker, json, Producer.Setup.of(topic, FooRecord.class));
    producer.write(createRecord("foo 1", 0));
    producer.write(createRecord("foo 2", 1));

    CountHolder holder = new CountHolder();
    dispatcher1 = createAndStart(records -> {
      holder.count++;
      Await.await(3, SECONDS).doWait();
      records.forEach(r -> list.add(r));
    }, "Group_1");

    Await.await(10, SECONDS).until(() -> holder.count > 0);

    // at this moment we have two records in progress(thus not committed)
    // a new dispatcher leads to rebalance partitions
    // but first dispatcher should hold two partitions until its records are processed(committed)
    dispatcher2 = createAndStart(records -> records.forEach(r -> list.add(r)), "Group_1");

    dispatcher1.stopAsync().awaitTerminated();
    dispatcher2.stopAsync().awaitTerminated();

    that(list).hasSize(2);
  }

  @Test
  public void shouldProcessRecordPerHandler() {
    ListMultimap<String, FooRecord> buckets = synchronizedListMultimap(create());

    Producer<FooRecord> producer = new Producer<>(broker, json, Producer.Setup.of(topic, FooRecord.class));
    producer.write(createRecord("foo 1", 0));
    producer.write(createRecord("foo 2", 1));

    dispatcher1 = createAndStart(records -> buckets.putAll(currentThread().getName(), records), "Group_2");

    Await.await(10, SECONDS).until(() -> buckets.size() == 2);

    dispatcher1.stopAsync().awaitTerminated();

    that(buckets.keySet()).hasOnly(
        dispatcher1.serviceName() + ":Handler<shard-0>",
        dispatcher1.serviceName() + ":Handler<shard-1>");
  }

  @Test
  public void shouldReProcessedRecordIfException() {
    Producer<FooRecord> producer = new Producer<>(broker, json, Producer.Setup.of(topic, FooRecord.class));
    producer.write(createRecord("foo 1", 0));

    CountHolder holder = new CountHolder();
    dispatcher1 = createAndStart(records -> {
      if (holder.count < 1) {
        holder.count++;
        throw new RuntimeException("Some error from receiver.");
      }
      records.forEach(r -> list.add(r));
    }, "Group_3");

    Await.await(10, SECONDS).until(() -> list.size() > 0);
    dispatcher1.stopAsync().awaitTerminated();

    that(list).hasSize(1);
  }

  class CountHolder {
    int count = 0;
  }

  private Dispatcher<FooRecord> createAndStart(Receiver<FooRecord> receiver, String group) {
    Dispatcher<FooRecord> dispatcher = new Dispatcher<>(broker, ExceptionSink.printingStackTrace(System.out), json, () -> receiver,
        new Dispatcher.Setup.Builder().topic(topic).group(group).type(FooRecord.class).build());
    dispatcher.startAsync().awaitRunning();
    return dispatcher;
  }

  private class FooRecord implements Keyed<Integer>, Sharded<Integer> {

    private String value;

    private FooRecord(String value) {this.value = value;}

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
