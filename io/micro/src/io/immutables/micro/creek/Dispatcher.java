package io.immutables.micro.creek;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.stream.Receiver;
import io.immutables.stream.Topic;
import io.immutables.micro.creek.Broker.AvailableRecords;
import io.immutables.micro.creek.Broker.LeasedRecords;
import io.immutables.micro.creek.Broker.Subscription;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.common.cache.*;
import com.google.common.collect.Iterators;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Service;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;

/**
 * @param <R> record type
 */
@Enclosing
public class Dispatcher<R> extends AbstractScheduledService {
  @Immutable
  public interface Conf {
    /** Topic to consume. */
    Topic topic();

    /** Type of record after unmarshaling. */
    Type type();

    /** Consumer group */
    Optional<String> group();

    /** Limit of records to be read per partition at once. */
    @Default
    default int limit() {
      return 10;
    }

    @Default
    default boolean autoCommit() {
      return true;
    }

    @Default
    default Duration pollInterval() {
      return Duration.ofMillis(20);
    }

    @Default
    default Duration idleReceiverTimeout() {
      return Duration.ofMinutes(5);
    }

    class Builder extends ImmutableDispatcher.Conf.Builder {}
  }

  private final OkJson json;
  private final Conf conf;
  private final Broker broker;
  private final Supplier<Receiver<R>> receiverSupplier;
  private final Codec<R> codec;

  private final LoadingCache<Integer, ShardHandler> shards;
  private Subscription subscription;

  @SuppressWarnings("unchecked") // safe unchecked via reflective type check
  public Dispatcher(
      Broker broker,
      OkJson json,
      Supplier<Receiver<R>> receiverSupplier, Conf conf) {
    this.broker = broker;
    this.json = json;
    this.conf = conf;
    this.receiverSupplier = receiverSupplier;
    this.codec = (Codec<R>) json.get(TypeToken.of(conf.type()));
    this.shards = CacheBuilder.newBuilder()
        .expireAfterAccess(conf.idleReceiverTimeout())
        .removalListener(new RemovalListener<Integer, ShardHandler>() {
          @Override
          public void onRemoval(RemovalNotification<Integer, ShardHandler> notification) {
            ShardHandler handler = notification.getValue();
            Service service = handler.stopAsync();
            handler.notifyAvailable();
            service.awaitTerminated();
          }
        })
        .build(new CacheLoader<Integer, ShardHandler>() {
          @Override
          public ShardHandler load(Integer key) throws Exception {
            ShardHandler handler = new ShardHandler(key);
            handler.startAsync().awaitRunning();
            return handler;
          }
        });
  }

  public Topic topic() {
    return conf.topic();
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
        conf.pollInterval().toNanos(), conf.pollInterval().toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  protected void startUp() throws Exception {
    subscription = broker.subscribe(conf.topic(), conf.group());
  }

  @Override
  protected void shutDown() throws Exception {
    shards.invalidateAll();
    subscription.close();
  }

  @Override
  protected void runOneIteration() throws Exception {
    for (AvailableRecords available : subscription.available()) {
      ShardHandler handler = shards.get(available.shard);
      if (handler.isRunning()) {
        handler.notifyAvailable();
      } else {
        // try to heal/recreate another handler
        // in case this handler is not running
        // notice that we just delay to the next iteration
        shards.invalidate(available.shard);
      }
    }
  }

  private final class ShardHandler extends AbstractExecutionThreadService {
    private final Object availableCondition = new Object();
    private final int shard;
    private Receiver<R> receiver;

    ShardHandler(int shard) {
      this.shard = shard;
    }

    @Override
    protected void startUp() throws Exception {
      receiver = receiverSupplier.get();
    }

    @Override
    protected void shutDown() throws Exception {
      if (receiver instanceof AutoCloseable) {
        ((AutoCloseable) receiver).close();
      }
    }

    @Override
    protected String serviceName() {
      return String.format("%s:%s/%d",
          getClass().getSimpleName(), conf.topic(), shard);
    }

    @Override
    protected void run() throws Exception {
      while (isRunning()) {
        tryReceiveRecords();
        synchronized (availableCondition) {
          if (isRunning()) availableCondition.wait();
        }
      }
    }

    void notifyAvailable() {
      synchronized (availableCondition) {
        availableCondition.notify();
      }
    }

    private void tryReceiveRecords() {
      Optional<LeasedRecords> leasedRecords = subscription.read(shard, conf.limit());
      if (leasedRecords.isEmpty()) return;

      ReceivedRecords records = new ReceivedRecords(leasedRecords.get());
      try {
        receiver.on(records);

        if (conf.autoCommit()) {
          records.commit();
        }
      } catch (Exception ex) {
        handleReceiverException(ex);
      }
    }

    private void handleReceiverException(Exception ex) {
      ex.printStackTrace(); // TODO improve handling )
    }
  }

  private final class ReceivedRecords implements Receiver.Records<R> {
    private boolean committed;
    private final LeasedRecords leased;

    public ReceivedRecords(LeasedRecords leased) {
      this.leased = leased;
    }

    /** Lazy transformed iterator */
    @Override
    public Iterator<R> iterator() {
      return Iterators.transform(leased.records.iterator(), r -> decode(r.value));
    }

    private R decode(@Nullable Object value) {
      // nulls can occur as tombstone records etc
      return value == null ? null : json.fromJson(value.toString(), codec);
    }

    @Override
    public Topic topic() {
      return conf.topic();
    }

    @Override
    public int shard() {
      return leased.shard;
    }

    @Override
    public int size() {
      return leased.records.size();
    }

    @Override
    public void commit() {
      if (!committed) {
        subscription.commit(leased.shard, leased.offset, leased.offset + size());
        committed = true;
      }
    }

    @Override
    public String toString() {
      return String.format("records[%d]:%s<%s>",
          size(),
          conf.topic(),
          conf.group().orElse(""));
    }
  }

  @Override
  protected String serviceName() {
    return String.format("%s:%s<%s>",
        getClass().getSimpleName(),
        conf.topic(),
        conf.group().orElse(""));
  }
}
