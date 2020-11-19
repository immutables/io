package io.immutables.micro.creek;

import io.immutables.stream.Topic;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import com.google.common.base.MoreObjects;
import com.google.common.collect.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

/**
 * In-memory stream/data broker similar to Kafka/Kinesis. Used primarily as a testbed to establish generalized data
 * streaming contracts and for testing. With time it would be less and less viable to use this implementation and
 * libraries like KStreams and others would mandate to use actual Kafka (in embedded mode for example).
 * <p>
 * Broker is thread safe, have some internal immutable, threadsafe and some not threadsafe components with proper
 * synchronization applied.
 * <ul>
 * <li>create, clear, topics list, count shards and [internal] get stream are guarded by monitor on
 * "streams" map
 * <li>Read/write operations coming from subscriptions and publications are synchronized on
 * respective stream (Shardset) object's monitor
 * <li>Everything else is recursively immutable or final
 * <li>Ticker is expected to return mononotically increasing ticks/timestamps, but otherwise we're
 * not dependent if it's thread-safe.
 * </ul>
 */
@ThreadSafe
public final class Broker {
  private static final long UNASSIGNED = 0; // explanatory constant for Lease.subscription == 0
  private static final long UNLEASED = 0; // explanatory constant for Lease.expires == 0

  private final AtomicLong subscriptionIdCounter = new AtomicLong();

  private final Ticker ticker;
  private final long leaseExpiresIn;
  private final long subscriptionExpiresIn;
  private final Map<Topic, Shardset> streams = new HashMap<>();

  public Broker(Ticker ticker, long subscriptionExpiresIn, long leaseExpiresIn) {
    requireNonNull(ticker, "ticker");
    checkArgument(subscriptionExpiresIn > 0, "subscriptionExpiresIn > 0, but was %s", subscriptionExpiresIn);
    checkArgument(leaseExpiresIn > 0, "leaseExpiresIn > 0, but was %s", leaseExpiresIn);

    this.ticker = ticker;
    this.leaseExpiresIn = leaseExpiresIn;
    this.subscriptionExpiresIn = subscriptionExpiresIn;
  }

  /**
   * Create stream by topic with specified number or shards. Internally stream is represented by shardset structure. We
   * cannot subscribe or publish to topics before they are created be sure to initialize topics beforehand. We cannot
   * remove streams after creation to avoid introducing complications with invalidation of subscription and
   * publications. But we can reset all streams using {@link #clear()}, then all offsets will be reset.
   * @param topic topic for the stream
   * @param shards number of shards
   * @return {@code this} for chained invokation
   */
  public Broker create(Topic topic, int shards) {
    requireNonNull(topic, "topic");
    checkArgument(shards > 0, "shards > 0");

    synchronized (streams) {
      streams.put(topic, new Shardset(shards));
    }
    return this;
  }

  public void clear() {
    synchronized (streams) {
      for (Shardset stream : streams.values()) {
        // intentionally use stream to synchronize
        // this is needed for subscriptions
        synchronized (stream) {
          stream.clear();
        }
      }
    }
  }

  public int countShards(Topic topic) {
    return existingStream(topic).count;
  }

  public Set<Topic> topics() {
    synchronized (streams) {
      return ImmutableSet.copyOf(streams.keySet());
    }
  }

  /**
   * Factory for the record objects. This doesn't write records to shards, just create them to be written by {@link
   * Publication}
   */
  public Record record(int shard, Object key, Object value) {
    return new Record(ticker.read(), shard, key, value);
  }

  /**
   * Source of ticks (preferably monotonically increasing) - discreet time. {@code System::nanoTime} or {@code
   * System::currentTimeMillis} could be good choices, as {@code AtomicLong::get} for testing. Mind the use of
   * correspondingly sized times (tick counts) for lease and subscription expiration for the {@link
   * Broker#Broker(Ticker, long, long)} constructor.
   */
  @FunctionalInterface
  public interface Ticker {
    long read();
  }

  public interface Publication {
    Topic topic();

    int shards();

    void write(Iterable<Record> records);
  }

  public interface Subscription extends AutoCloseable {

    Topic topic();

    Optional<String> group();

    List<AvailableRecords> available();

    List<LeasedRecords> read(int limitPerShard);

    Optional<LeasedRecords> read(int shard, int limitPerShard);

    void commit(int shard, int currentOffset, int commitOffset);

    /** Can check that subscription / connection is available */
    boolean isActual();

    List<Integer> shards();

    @Override
    void close();
  }

  public Publication publish(Topic topic) {
    Shardset stream = existingStream(topic);
    return new Publication() {
      @Override
      public void write(Iterable<Record> records) {
        synchronized (stream) {
          stream.write(records);
        }
      }

      @Override
      public Topic topic() {
        return topic;
      }

      @Override
      public int shards() {
        return stream.count;
      }

      @Override
      public String toString() {
        return MoreObjects.toStringHelper(Publication.class)
            .addValue(topic + ":" + shards())
            .toString();
      }
    };
  }

  private Shardset existingStream(Topic topic) {
    Shardset stream;
    synchronized (streams) {
      stream = streams.get(topic);
    }
    if (stream == null) throw new NoSuchElementException(
        String.format("No such topic %s, known are: %s", topic, topics()));
    return stream;
  }

  public Subscription subscribe(Topic topic, Optional<String> groupName) {
    return new Subscription() {
      // We use monitor lock on stream to guard both stream
      // its subscriber groups, streams.
      final Shardset stream = existingStream(topic);
      // we use subscription id so that we can detach Groups from subscriptions
      // subscription can be come stale and being dropped
      final long id = subscriptionIdCounter.incrementAndGet(); // start with 1 and up

      final String uniqueGroupId = groupName.orElseGet(() -> "__" + System.identityHashCode(this));
      {
        synchronized (stream) {
          getGroup().refreshSubscription(id);
        }
      }
      private SubscriberGroup getGroup() {
        return stream.getGroup(uniqueGroupId);
      }

      @Override
      public Topic topic() {
        return topic;
      }

      @Override
      public Optional<String> group() {
        return groupName;
      }

      @Override
      public List<Integer> shards() {
        synchronized (stream) {
          return getGroup().assignedShards(id);
        }
      }

      @Override
      public List<AvailableRecords> available() {
        synchronized (stream) {
          return getGroup().available(id);
        }
      }

      @Override
      public List<LeasedRecords> read(int limit) {
        synchronized (stream) {
          return getGroup().read(id, limit);
        }
      }

      @Override
      public Optional<LeasedRecords> read(int shard, int limit) {
        synchronized (stream) {
          return getGroup().read(id, shard, limit);
        }
      }

      @Override
      public void commit(int shard, int currentOffset, int commitOffset) {
        synchronized (stream) {
          getGroup().commit(id, shard, currentOffset, commitOffset);
        }
      }

      @Override
      public boolean isActual() {
        synchronized (stream) {
          return getGroup().isSubscribed(id);
        }
      }

      @Override
      public void close() {
        synchronized (stream) {
          getGroup().dropSubscription(id);
        }
      }

      @Override
      public String toString() {
        return MoreObjects.toStringHelper(Subscription.class)
            .addValue(topic)
            .add("id", id)
            .toString();
      }
    };
  }

  @ThreadSafe
  public static final class AvailableRecords {
    public final int shard;
    public final int offset;
    public final int count;

    private AvailableRecords(int shard, int offset, int count) {
      this.shard = shard;
      this.offset = offset;
      this.count = count;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("shard", shard)
          .add("offset", offset)
          .add("count", count)
          .toString();
    }
  }

  @javax.annotation.concurrent.Immutable
  public static final class LeasedRecords {
    public final int shard;
    public final int offset;
    public final ImmutableList<Record> records;

    private LeasedRecords(int shard, int offset, Iterable<Record> records) {
      this.shard = shard;
      this.offset = offset;
      this.records = ImmutableList.copyOf(records);
      assert !this.records.isEmpty();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("shard", shard)
          .add("offset", offset)
          .add("count", this.records.size())
          .toString();
    }
  }

  @javax.annotation.concurrent.Immutable
  public static final class Record {
    final long timestamp;
    final int shard;
    final @Nullable Object key;
    final @Nullable Object value;

    private Record(long timestamp, int shard, @Nullable Object key, @Nullable Object value) {
      this.timestamp = timestamp;
      this.shard = shard;
      this.key = key;
      this.value = value;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("shard", shard)
          .add("key", key)
          .addValue(value)
          .toString();
    }
  }

  // Synchronized on 'this' but externally to cover sibling operation on groups/records etc
  @NotThreadSafe
  private final class Shardset {
    private final ImmutableList<ArrayList<Record>> shards;
    private final Map<String, SubscriberGroup> groups = new ConcurrentHashMap<>();

    final int count;

    Shardset(int count) {
      this.count = count;
      this.shards = IntStream.range(0, count)
          .<ArrayList<Record>>mapToObj(i -> new ArrayList<>())
          .collect(toImmutableList());
    }

    SubscriberGroup getGroup(String name) {
      return groups.computeIfAbsent(name, key -> new SubscriberGroup(name, this));
    }

    int available(int shard, int offset) {
      return Math.max(0, shards.get(shard).size() - offset);
    }

    List<Record> read(int shard, int offset, int limit) {
      List<Record> records = shards.get(shard);
      checkPositionIndex(offset, records.size());
      int upto = Math.min(offset + limit, records.size());
      return records.subList(offset, upto);
    }

    void write(Iterable<Record> records) {
      Multimaps.index(records, r -> r.shard)
          .asMap()
          .forEach((shard, recordsByShard) -> {
            checkElementIndex(shard, shards.size(), "records[*].shard");
            shards.get(shard).addAll(recordsByShard);
          });
    }

    void clear() {
      shards.forEach(List::clear);
      groups.clear();
    }
  }

  /**
   * Each subscriber using the same group name will end up being managed by the same instance of {@link
   * SubscriberGroup}. Then each record reading is coordinated via group which tracks subscriptions, leases and their
   * expirations and offset tracking.
   */
  // access guarded by stream, but externally
  @NotThreadSafe
  private final class SubscriberGroup {
    /** Group name – identifier. */
    private final String name;
    /** Stream from which we read records */
    private final Shardset stream;
    /** Array of leases per shard, index == lease.shard. */
    private final Lease[] leases;
    /** Tracks subscribers by subscription id key to the expiration timestamp/tick. */
    private final Map<Long, Long> subscriptionExpirations = new LinkedHashMap<>();

    private boolean requiresRebalance = false;

    SubscriberGroup(String name, Shardset stream) {
      this.name = name;
      this.stream = stream;
      this.leases = IntStream.range(0, stream.count)
          .mapToObj(Lease::new)
          .toArray(Lease[]::new);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("name", name)
          .add("leases", leases)
          .add("subscription", subscriptionExpirations)
          .toString();
    }

    /**
     * Each lease tracks the assignment of a shard to subscription. {@code subscription == 0} //UNASSIGNED represents
     * untaken, unassigned shard within SubscriberGroup. {@code expires == 0} //UNLEASED represents shard that is not
     * waiting for the read records to be commited. Is some subscription reads
     */
    @NotThreadSafe
    private final class Lease {
      /** Last commited offset. */
      int offset;
      /** Subscription currently assigned */
      long subscription;
      /** If available records were leased */
      long expires;

      final int shard;

      Lease(int shard) {
        this.shard = shard;
      }

      @Override
      public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("shard", shard)
            .add("offset", offset)
            .add("subscription", subscription)
            .add("expires", expires)
            .toString();
      }
    }

    ImmutableList<Integer> assignedShards(long subscription) {
      // do not refresh subscription here, if we close, we will not auto-reconnect
      expireSubscriptions();
      expireLeases();
      rebalance();
      return Arrays.stream(leases)
          .filter(l -> l.subscription == subscription)
          .map(l -> l.shard)
          .collect(toImmutableList());
    }

    ImmutableList<AvailableRecords> available(long subscription) {
      refreshSubscription(subscription);
      expireSubscriptions();
      expireLeases();
      rebalance();
      return availableRecords(subscription);
    }

    ImmutableList<LeasedRecords> read(long subscription, int limit) {
      refreshSubscription(subscription);
      expireSubscriptions();
      expireLeases();
      rebalance();
      return leaseRecords(subscription, limit);
    }

    Optional<LeasedRecords> read(long subscription, int shard, int limit) {
      refreshSubscription(subscription);
      expireSubscriptions();
      expireLeases();
      rebalance();
      return leaseRecords(subscription, shard, limit);
    }

    private ImmutableList<AvailableRecords> availableRecords(long subscription) {
      ImmutableList.Builder<AvailableRecords> result = ImmutableList.builder();
      for (Lease l : leases) {
        if (l.subscription == subscription) {
          int available = stream.available(l.shard, l.offset);
          if (available > 0) {
            result.add(new AvailableRecords(l.shard, l.offset, available));
          }
        }
      }
      return result.build();
    }

    private Optional<LeasedRecords> leaseRecords(long subscription, int shard, int limit) {
      long now = ticker.read();

      for (Lease l : leases) {
        if (l.subscription == subscription && l.shard == shard) {
          List<Record> records = stream.read(l.shard, l.offset, limit);
          if (!records.isEmpty()) {
            l.expires = now + leaseExpiresIn;
            return Optional.of(new LeasedRecords(l.shard, l.offset, records));
          }
          break;
        }
      }
      return Optional.empty();
    }

    private ImmutableList<LeasedRecords> leaseRecords(long subscription, int limit) {
      long now = ticker.read();

      ImmutableList.Builder<LeasedRecords> result = ImmutableList.builder();
      for (Lease l : leases) {
        if (l.subscription == subscription) {
          List<Record> records = stream.read(l.shard, l.offset, limit);
          if (!records.isEmpty()) {
            l.expires = now + leaseExpiresIn;
            result.add(new LeasedRecords(l.shard, l.offset, records));
          }
        }
      }
      return result.build();
    }

    void refreshSubscription(long subscription) {
      long expires = ticker.read() + subscriptionExpiresIn;
      if (subscriptionExpirations.put(subscription, expires) == null) {
        // if there was value already assigned and we do not require rebalance,
        // just refreshed
        requiresRebalance = true;
      }
    }

    void dropSubscription(long subscription) {
      if (subscriptionExpirations.remove(subscription) != null) {
        requiresRebalance = true;
      }
    }

    private void expireSubscriptions() {
      long now = ticker.read();
      if (subscriptionExpirations.entrySet().removeIf(e -> e.getValue() <= now)) {
        requiresRebalance = true;
      }
    }

    private void expireLeases() {
      long now = ticker.read();
      for (Lease l : leases) {
        // we expire lease and unassing it when one of two conditions are met:
        // 1. When lease is assigned to a subscription which is already expired
        // 2. When lease is assinged to subscription which failed to commit open lease in time
        if ((l.expires > UNLEASED && l.expires <= now)
            || (l.subscription != UNASSIGNED && !subscriptionExpirations.containsKey(l.subscription))) {
          l.subscription = UNASSIGNED;
          l.expires = UNLEASED;
          requiresRebalance = true;
        }
      }
    }

    boolean isSubscribed(long id) {
      expireSubscriptions();
      return subscriptionExpirations.containsKey(id);
    }

    void commit(long subscription, int shard, int currentOffset, int commitOffset) {
      refreshSubscription(subscription);
      expireLeases();
      Lease lease = leases[shard];
      checkState(lease.subscription == subscription, "Lease is not owned, cannot commit");
      checkState(lease.offset == currentOffset, "Current recorded offset mismatch");

      lease.offset = commitOffset;
      lease.expires = UNLEASED;
    }

    private void rebalance() {
      if (!requiresRebalance) return;
      // no subscription to rebalance (need corner-casing?)
      if (subscriptionExpirations.isEmpty()) return;

      new ShardBalancer().rebalance();
      // mark as done
      requiresRebalance = false;
    }

    /**
     * As dictated by {@code Comparable.compareTo} implementation, the highest priority will have unassigned shards,
     * then the ones coming from subscriptions from higher count of assigned shards.
     */
    private class Donation implements Comparable<Donation> {
      final long subscription;
      final Lease lease;
      final int remaining;

      Donation(long subscription, Lease lease, int remaining) {
        this.subscription = subscription;
        this.lease = lease;
        this.remaining = remaining;
      }

      @Override
      public int compareTo(Donation o) {
        return ComparisonChain.start()
            .compareTrueFirst(lease.subscription == UNASSIGNED, o.lease.subscription == UNASSIGNED)
            .compare(o.remaining, remaining)
            .result();
      }
    }

    /**
     * Implements rebalancing shard assignments withing group of subscriptions as those subscriptions come and go, we
     * try to maintain fair number of shards assignmented to subscriptions, given that shards are not actively leased
     * for reading records. As we can assign shard to no more than one partion, all superflous subscriptions will starve
     * i.e. receive not assignments if all shards are already taken. During rebalancing we prioritize that subscriptions
     * with greated number of assignments share those with the subscriptions with the least number of shards.
     */
    @NotThreadSafe
    private final class ShardBalancer {
      final int minimum = 1;
      final Multimap<Long, Lease> leasesBySubscription =
          HashMultimap.create(Multimaps.index(Arrays.asList(leases), l -> l.subscription));

      /**
       * Donations are potential superfluous shard assignments that we use to fill starving and under-assigned
       * subscribers.
       */
      final PriorityQueue<Donation> donations = prioritizeDonations(minimum);
      /**
       * Rebalancing recipients/subscriptions.
       */
      final PriorityQueue<Long> recipients = prioritizeRecipients();

      PriorityQueue<Donation> prioritizeDonations(int minimum) {
        assert minimum > 0;

        PriorityQueue<Donation> queue = new PriorityQueue<>();
        leasesBySubscription.asMap().forEach((subscription, leases) -> {
          if (subscription == UNASSIGNED) {
            for (Lease l : leases) {
              queue.add(new Donation(UNASSIGNED, l, Integer.MAX_VALUE));
            }
          } else {
            int remaining = leases.size();
            for (Lease l : leases) {
              if (l.expires == UNLEASED // it's important to not give away leased partitions
                  && remaining > minimum) {
                // each next donation would be tracked as less remaining, so
                // it would be of a lesser priority than the donation from where there's more
                // remaining. This is good alternative to externalize current count and constantly
                // re-adding elements to the queue to readjust priority (as we doing for
                // recipients)
                queue.add(new Donation(subscription, l, remaining));
                remaining--;
              }
            }
          }
        });
        return queue;
      }

      PriorityQueue<Long> prioritizeRecipients() {
        Comparator<Long> leastShardsFirst = (a, b) -> countShards(a) - countShards(b);
        PriorityQueue<Long> queue = new PriorityQueue<>(leastShardsFirst);
        queue.addAll(subscriptionExpirations.keySet());
        return queue;
      }

      void accept(long recipient, Donation donation) {
        // first we reassign lease to subscription
        donation.lease.subscription = recipient;
        // then changing our working multimap
        leasesBySubscription.remove(donation.subscription, donation.lease);
        leasesBySubscription.put(recipient, donation.lease);
        // re-adding recipient (was already removed by poll to rearrange priority queue
        // due to changes in `leasesBySubscription`
        recipients.add(recipient);
        // re-adding donor to rearrange recipient priority queue
        // due to changes in `leasesBySubscription` (changed counts)
        if (donation.subscription != UNASSIGNED) {
          recipients.remove(donation.subscription);
          recipients.add(donation.subscription);
        }
      }

      void rebalance() {
        for (Donation d; (d = donations.poll()) != null; ) {
          // there always should be one or more recipients cycling by design
          long r = recipients.remove();
          // unassigned should not ever be here and can only be added by broken code
          assert r != UNASSIGNED;
          // the condition below is when our donation is no longer that beneficial
          // to the recipient, i.e. when proceeding further will actually
          // result in disbalance
          if (countShards(r) >= d.remaining) break;
          // if we properly checked previous check we would never reach
          // such recipient that have its own donation
          assert r != d.subscription;
          accept(r, d);
        }
      }

      private int countShards(long subscription) {
        return leasesBySubscription.get(subscription).size();
      }
    }
  }
}
