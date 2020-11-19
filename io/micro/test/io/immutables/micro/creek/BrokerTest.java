package io.immutables.micro.creek;

import io.immutables.stream.Topic;
import io.immutables.that.Assert;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import com.google.common.collect.Iterables;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class BrokerTest {
  static final int EXPIRE_LEASE = 5;
  static final int EXPIRE_SUB = 10;
  static final Topic A = Topic.of("A");
  static final Topic B = Topic.of("B");
  final AtomicLong tick = new AtomicLong();
  final Broker broker = new Broker(tick::get, EXPIRE_SUB, EXPIRE_LEASE);

  @Test
  public void createStreams() {
    broker.create(A, 1);
    broker.create(B, 2);

    that(broker.topics()).hasOnly(A, B);
    that(broker.countShards(A)).is(1);
    that(broker.countShards(B)).is(2);
  }

  @Test
  public void wrongTopicOrShardIndex() {
    Assert.that(() -> subscribe(A)).thrown(NoSuchElementException.class);

    broker.create(B, 1);

    that(() -> broker.publish(A)).thrown(NoSuchElementException.class)
        .is(e -> e.getMessage().contains("B")); // message contains known topics
    that(() -> publish(B, record(2, "A"))).thrown(IndexOutOfBoundsException.class);
  }

  @Test
  public void subscribeAndClose() {
    broker.create(A, 1);

    Broker.Subscription s = subscribe(A);
    that(s.topic()).equalTo(A);
    that(s.shards()).isOf(0);
    s.close();
    that(s.shards()).isEmpty();
  }

  @Test
  public void subscribeAndAssingShards() {
    broker.create(A, 2);

    Broker.Subscription s1 = subscribe(A);
    Broker.Subscription s2 = subscribe(A);

    that(s1.shards()).isOf(0, 1);
    that(s2.shards()).isOf(0, 1);
  }

  @Test
  public void independentSubscriptionsAssingAllShards() {
    broker.create(A, 2);
    Broker.Subscription s1 = subscribe(A);
    Broker.Subscription s2 = subscribe(A);

    broker.create(B, 3);
    Broker.Subscription s3 = subscribe(B);
    Broker.Subscription s4 = subscribe(B);

    that(s1.shards()).isOf(0, 1);
    that(s2.shards()).isOf(0, 1);
    that(s3.shards()).isOf(0, 1, 2);
    that(s4.shards()).isOf(0, 1, 2);
  }

  @Test
  public void groupSubscriptionsEvenSplitShards() {
    broker.create(A, 3);

    Broker.Subscription s1 = subscribe(A, "A");
    Broker.Subscription s2 = subscribe(A, "A");
    Broker.Subscription s3 = subscribe(A, "A");

    that(s1.shards()).hasSize(1);
    that(s2.shards()).hasSize(1);
    that(s3.shards()).hasSize(1);
    that(Iterables.concat(s1.shards(), s2.shards(), s3.shards()))
        .hasOnly(0, 1, 2);
  }

  @Test
  public void groupSubscriptionsUnevenSplitShards() {
    broker.create(A, 3);

    Broker.Subscription s1 = subscribe(A, "A");
    Broker.Subscription s2 = subscribe(A, "A");

    that(s1.shards().size() != s2.shards().size()).orFail("equal number of shards");
    that(s1.shards().size() + s2.shards().size()).is(3);
  }

  @Test
  public void groupSubscriptionsUnevenStarveShards() {
    broker.create(A, 2);

    Broker.Subscription s1 = subscribe(A, "A");
    Broker.Subscription s2 = subscribe(A, "A");
    Broker.Subscription s3 = subscribe(A, "A");
    Broker.Subscription s4 = subscribe(A, "A");

    List<List<Integer>> shards =
        Arrays.asList(s1.shards(), s2.shards(), s3.shards(), s4.shards());

    that(shards.stream().flatMap(Collection::stream)).hasOnly(0, 1);
    that(shards.stream()
        .map(List::size)
        .collect(Collectors.toSet())).hasOnly(0, 1);
    that(shards.stream().mapToInt(List::size).sum()).is(2);
  }

  @Test
  public void simpleExpireSubscription() {
    broker.create(A, 1);

    Broker.Subscription s1 = subscribe(A);

    that(s1.isActual()).is(true);
    that(s1.shards()).isOf(0);

    tick.addAndGet(EXPIRE_SUB);

    that(s1.isActual()).is(false);
    that(s1.shards()).isEmpty();
  }

  @Test
  public void expireSubscriptionReassignShard() {
    broker.create(A, 1);

    Broker.Subscription s1 = subscribe(A, "A");
    that(s1.isActual()).is(true);
    that(s1.shards()).isOf(0);

    tick.addAndGet(EXPIRE_SUB - 1); // just one tick before expire p1

    Broker.Subscription s2 = subscribe(A, "A");
    that(s2.isActual()).is(true);
    that(s2.shards()).isEmpty();

    tick.addAndGet(1); // now, p1 would expire, shard will be relocated to p2

    that(s1.isActual()).is(false);
    that(s1.shards()).isEmpty();

    that(s2.isActual()).is(true);
    that(s2.shards()).isOf(0);
  }

  @Test
  public void publishAndReceiveWithOffsetAndLimits() {
    broker.create(B, 2);

    that(broker.publish(B).topic()).equalTo(B);
    that(broker.publish(B).shards()).is(2);

    publish(B,
        record(1, "10"),
        record(0, "00"),
        record(0, "01"));

    Broker.Subscription s1 = subscribe(B);
    int readLimit = 1;
    List<Broker.LeasedRecords> a1 = s1.read(readLimit);

    Assert.that(a1).hasSize(2); // per 2 shards, shard indeces in ascending order
    that(a1.get(0).records).hasSize(readLimit); // per limit
    that(a1.get(0).records.get(0).value).hasToString("00");
    that(a1.get(1).records).hasSize(readLimit); // per limit
    that(a1.get(1).records.get(0).value).hasToString("10");

    s1.commit(0, a1.get(0).offset, readLimit);
    s1.commit(1, a1.get(1).offset, readLimit);

    // next advance after commit
    a1 = s1.read(readLimit);
    Assert.that(a1).hasSize(1); // only 1 new message remains in
    that(a1.get(0).records).hasSize(readLimit); // per limit
    that(a1.get(0).records.get(0).value).hasToString("01");

    // independent read with high limit, we read all shards in order
    List<Broker.LeasedRecords> a2 = subscribe(B).read(100);
    that(a2.stream()
        .flatMap(rs -> rs.records.stream())
        .map(r -> r.value)).isOf("00", "01", "10");
  }

  @Test
  public void publishAndGetAvailable() {
    broker.create(B, 3);

    publish(B,
        record(0, "00"),
        record(0, "01"),
        record(1, "10"));

    Broker.Subscription s1 = subscribe(B);
    List<Broker.AvailableRecords> a1 = s1.available();

    Assert.that(a1).hasSize(2);
    that(a1.get(0).shard).is(0);
    that(a1.get(0).count).is(2);
    that(a1.get(1).shard).is(1);
    that(a1.get(1).count).is(1);
  }

  @Test
  public void reassignAllButLeasedShards() {
    broker.create(A, 3);

    Broker.Subscription s1 = subscribe(A, "A");
    that(s1.shards()).isOf(0, 1, 2);

    publish(A,
        record(0, "A0"),
        record(2, "A2")); // no message on shard 1, will not lease it to s1 on read

    s1.read(1);// read and lease shards 0 and 2 (reading limit 1 message)

    Broker.Subscription s2 = subscribe(A, "A");
    Broker.Subscription s3 = subscribe(A, "A");

    that(s1.shards()).isOf(0, 2); // these are not relocated while leased
    that(s2.shards()).isOf(1); // s2 received unleased 1 (s2 connected and grabbed it)
    that(s3.shards()).isEmpty(); // s3 is starving

    tick.addAndGet(EXPIRE_LEASE); // leases will expire now and shards will get evenly distributed
    // note that don't try to guess which one will be where
    that(s1.shards()).hasSize(1);
    that(s2.shards()).hasSize(1);
    that(s3.shards()).hasSize(1);
  }

  @Test
  public void leaseAndCommitOneShard() {
    broker.create(A, 2);

    Broker.Subscription s1 = subscribe(A, "A");

    publish(A,
        record(0, "A0"),
        record(1, "A1"));

    Optional<Broker.LeasedRecords> a1 = s1.read(0, 1); // read and lease shard 0
    assert a1.isPresent();
    that(a1.get().records).hasSize(1); // only one was there anyway
    that(a1.get().records.get(0).value).hasToString("A0");
    s1.commit(0, a1.get().offset, 1);

    that(s1.read(0, 1)).isEmpty(); // read shard 0 to the end

    // reading using all shards will return remaining message in shard 1
    List<Broker.LeasedRecords> a2 = s1.read(10); // just enough / superflous limit

    Assert.that(a2).hasSize(1); // per 2 shards, shard indeces in ascending order
    that(a2.get(0).records).hasSize(1); // remaining 1 message
    that(a2.get(0).records.get(0).value).hasToString("A1");
  }

  @Test
  public void clear() {
    broker.create(A, 2);

    Broker.Subscription s1 = subscribe(A, "A");

    publish(A, record(0, "A0"));
    that(s1.available()).hasSize(1);

    broker.clear();
    that(s1.available()).hasSize(0);
  }

  private Broker.Record record(int shard, String value) {
    return broker.record(shard, null, value);
  }

  private void publish(Topic topic, Broker.Record... records) {
    broker.publish(topic).write(List.of(records));
  }

  private Broker.Subscription subscribe(Topic topic) {
    return broker.subscribe(topic, Optional.empty());
  }

  private Broker.Subscription subscribe(Topic topic, String group) {
    return broker.subscribe(topic, Optional.of(group));
  }
}
