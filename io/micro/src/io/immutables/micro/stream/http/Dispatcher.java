package io.immutables.micro.stream.http;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.stream.Receiver;
import io.immutables.stream.Topic;
import io.immutables.micro.ExceptionSink;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.common.cache.*;
import com.google.common.collect.Iterators;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractScheduledService;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.util.concurrent.AbstractScheduledService.Scheduler.newFixedDelaySchedule;
import static com.google.common.util.concurrent.Service.State.FAILED;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@Enclosing
public class Dispatcher<R> extends AbstractScheduledService {

  private final BrokerApi brokerApi;

  private final OkJson json;
  private final Setup setup;
  private final Supplier<Receiver<R>> receiverSupplier;
  private final LoadingCache<Integer, PartitionHandler> handlers;
  private final Optional<String> group;
  private final String clientId;
  private final String topic;

  private final Codec<R> codec;

  @SuppressWarnings("unchecked")
  public Dispatcher(BrokerApi brokerApi, ExceptionSink sink, OkJson json, Supplier<Receiver<R>> receiver, Setup setup) {
    this.brokerApi = brokerApi;
    this.json = json;
    this.setup = setup;
    this.receiverSupplier = receiver;
    this.group = setup.group();
    this.clientId = "Client_" + System.identityHashCode(this);
    this.topic = setup.topic().value();
    this.handlers = CacheBuilder.newBuilder()
        .expireAfterAccess(setup.idleReceiverTimeout())
        .removalListener(new RemovalListener<Integer, PartitionHandler>() {
          @Override
          public void onRemoval(RemovalNotification<Integer, PartitionHandler> notification) {
            PartitionHandler handler = notification.getValue();
            if (handler.state() == FAILED) {
              sink.unhandled(handler.failureCause());
            }
            handler.stopAsync();
          }
        })
        .build(new CacheLoader<Integer, PartitionHandler>() {
          @Override
          public PartitionHandler load(Integer key) {
            PartitionHandler handler = new PartitionHandler(key);
            handler.startAsync().awaitRunning();
            return handler;
          }
        });

    this.codec = (Codec<R>) json.get(TypeToken.of(setup.type()));
  }

  @Override protected void startUp() throws Exception {
    waitUntilConnected();
  }

  private void waitUntilConnected() throws InterruptedException {
    AssertionError ex = new AssertionError("Cannot connect to stream broker. " + BrokerApi.ClientId.clientId(clientId
        , group, topic));

    for (int i = 1; i < 11; i++) {
      try {
        runOneIteration();
        return;
      } catch (Exception e) {
        ex.addSuppressed(e);
        Thread.sleep(i * 1000);
      }
    }

    throw ex;
  }

  @Override protected void runOneIteration() throws Exception {
    List<BrokerApi.ShardOffset> offsets = new ArrayList<>();
    handlers.asMap().values().forEach(h -> h.getProcessedRecords().ifPresent(offsets::add));

    BrokerApi.DispatcherResponse response =
        brokerApi.poll(BrokerApi.DispatcherRequest.with(BrokerApi.ClientId.clientId(clientId, group, topic), offsets));

    for (BrokerApi.Records r : response.records()) {
      getHandler(r.shard()).handleAsync(r);
    }
  }

  private PartitionHandler getHandler(int shard) throws ExecutionException {
    PartitionHandler handler = handlers.get(shard);
    if (handler.isRunning()) {
      return handler;
    }
    // in case this handler is not running discard it and let cache to load a new one
    handlers.invalidate(shard);
    return handlers.get(shard);
  }

  @Override protected Scheduler scheduler() {
    return newFixedDelaySchedule(setup.pollInterval().toNanos(), setup.pollInterval().toNanos(), NANOSECONDS);
  }

  @Override
  protected String serviceName() {
    return String.format("Dispatcher(%s:%s<topic:%s>)", group.orElse(""), clientId, topic);
  }

  @Override
  protected void shutDown() throws Exception {
    // sync wait for all handlers to gracefully shut down(finish current records)
    // collect results and send unsubscribe request
    List<BrokerApi.ShardOffset> offsets = new ArrayList<>();
    handlers.asMap().values().forEach(h -> {
      h.stopAsync();// will sync invoke triggerShutdown() to finish current records
      h.getProcessedRecords().ifPresent(offsets::add);
    });
    handlers.invalidateAll();

    brokerApi.unsubscribe(BrokerApi.DispatcherRequest.with(BrokerApi.ClientId.clientId(clientId, group, topic),
        offsets));
  }

  private class PartitionHandler extends AbstractExecutionThreadService {

    private final Receiver<R> receiver;
    private final BlockingQueue<ReceivedRecords> receivedRecords = new LinkedBlockingQueue<>(1);
    private final BlockingQueue<BrokerApi.ShardOffset> processedRecords = new LinkedBlockingQueue<>(1);
    private final int partition;

    private PartitionHandler(int partition) {
      this.partition = partition;
      this.receiver = receiverSupplier.get();
    }

    private void handleAsync(BrokerApi.Records shardRecords) throws InterruptedException {
      receivedRecords.put(new ReceivedRecords(shardRecords));
    }

    private Optional<BrokerApi.ShardOffset> getProcessedRecords() {
      return ofNullable(processedRecords.poll());
    }

    @Override
    protected void run() throws Exception {
      while (isRunning()) {
        ReceivedRecords r = receivedRecords.take();
        synchronized (receivedRecords) {
          try {
            receiver.on(r);
            if (setup.autoCommit()) {
              r.commit();
            }
            //todo what if records are uncommitted neither manually or auto? throw exception?
          } catch (Exception e) {
            // current shard handler is going to die and new one will be recreated on next iteration
            // need to reset fetch offset to process same records on next iteration
            // overall it can cause to have infinite loop of processing poison record

            //need to send offset prior processing current records to broker
            long offsetBeforeRecords = r.records.offset() - r.size();
            processedRecords.put(BrokerApi.ShardOffset.of(partition, offsetBeforeRecords));
            // another things that we can do is create unhandled records queue
            // and/or commit offset before processing
            throw e;
          }
        }
      }
    }

    @Override
    protected void triggerShutdown() {
      // synchronized is here to wait for records to be processed
      synchronized (receivedRecords) {
        try {
          if (receiver instanceof AutoCloseable) {
            ((AutoCloseable) receiver).close();
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    protected String serviceName() {
      return format("%s:Handler<shard-%d>", Dispatcher.this.serviceName(), partition);
    }

    private class ReceivedRecords implements Receiver.Records<R> {

      private boolean committed;
      private final BrokerApi.Records records;

      private ReceivedRecords(BrokerApi.Records records) {
        this.records = records;
      }

      @Override
      public Iterator<R> iterator() {
        return Iterators.transform(records.records().iterator(), this::decode);
      }

      private @Nullable R decode(@Nullable BrokerApi.Record record) {
        // nulls can occur as tombstone records etc
        return record == null ? null : json.fromJson(record.value(), codec);
      }

      @Override
      public Topic topic() {
        return Topic.of(topic);
      }

      @Override
      public int shard() {
        return partition;
      }

      @Override
      public int size() {
        return records.size();
      }

      @Override
      public void commit() {
        if (!committed) {
          try {
            processedRecords.put(BrokerApi.ShardOffset.of(partition, records.offset()));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          committed = true;
        }
      }

      @Override public String toString() {
        return toStringHelper(this)
            .add("topic", topic)
            .add("shard", partition)
            .add("size", size())
            .add("offset", records.offset())
            .toString();
      }
    }
  }

  @Immutable
  public interface Setup {

    Topic topic();

    Type type();

    Optional<String> group();

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

    class Builder extends ImmutableDispatcher.Setup.Builder {}
  }
}
