package io.immutables.micro.kafka;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.micro.MicroInfo;
import io.immutables.micro.Servicelet;
import io.immutables.stream.Receiver;
import io.immutables.stream.Topic;
import io.immutables.micro.ExceptionSink;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.common.base.Joiner;
import com.google.common.cache.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractScheduledService;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.util.concurrent.AbstractScheduledService.Scheduler.newFixedDelaySchedule;
import static com.google.common.util.concurrent.Service.State.FAILED;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST;
import static org.apache.kafka.clients.consumer.OffsetResetStrategy.LATEST;

@Enclosing
public class Dispatcher<R> extends AbstractScheduledService {

  private final Setup setup;
  private final Consumer<String, String> consumer;
  private final Supplier<Receiver<R>> receiverSupplier;
  private final LoadingCache<Integer, PartitionHandler> handlers;
  private final Codec<R> codec;
  private final OkJson json;
  private final String groupId;
  private final String clientId;

  private boolean partitionsAssigned = false;

  public Dispatcher(
      Servicelet.Name servicelet,
      KafkaModule.BrokerInfo brokerInfo, ExceptionSink exceptionSink, OkJson json,
      Supplier<Receiver<R>> receiverSupplier, Setup setup) {
    this.json = json;

    //this.groupId = setup.group().orElseGet(() -> "Group_" + System.identityHashCode(this));
    //this.clientId = "Client_" + System.identityHashCode(this);

    this.clientId = servicelet + "__" + RuntimeInfo.key();
    this.groupId = setup.group().orElse("");

    this.setup = setup;
    this.codec = (Codec<R>) json.get(TypeToken.of(setup.type()));
    this.receiverSupplier = receiverSupplier;
    this.consumer = createConsumer(brokerInfo, setup);
    this.handlers = CacheBuilder.newBuilder()
        .expireAfterAccess(setup.idleReceiverTimeout())
        .removalListener(new RemovalListener<Integer, PartitionHandler>() {
          @Override
          public void onRemoval(RemovalNotification<Integer, PartitionHandler> notification) {
            PartitionHandler handler = notification.getValue();
            if (handler.state() == FAILED) {
              exceptionSink.unhandled(handler.failureCause());
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
  }

  private Consumer<String, String> createConsumer(KafkaModule.BrokerInfo brokerInfo, Setup setup) {
    Properties props = new Properties();
    props.put(BOOTSTRAP_SERVERS_CONFIG, brokerInfo.connect());
    props.put(CLIENT_ID_CONFIG, clientId);
    props.put(GROUP_ID_CONFIG, !groupId.isEmpty() ? groupId : clientId);
    props.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(MAX_POLL_RECORDS_CONFIG, setup.maxPollRecords());
    props.put(CONNECTIONS_MAX_IDLE_MS_CONFIG, setup.idleReceiverTimeout().toMillis());
    props.put(ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    props.put(AUTO_OFFSET_RESET_CONFIG, EARLIEST.name().toLowerCase());
    props.putAll(brokerInfo.setup().props());
    return new KafkaConsumer<>(props);
  }

  public boolean connectedToPartitions() {
    return partitionsAssigned;
  }

  public Topic topic() {
    return setup.topic();
  }

  @Override
  protected void runOneIteration() throws Exception {

    for (PartitionHandler handler : handlers.asMap().values()) {
      handler.getResult().ifPresent(result -> {
        if (result.type == OffsetType.COMMIT) {
          consumer.commitSync(ImmutableMap.of(result.topicPartition, new OffsetAndMetadata(result.offset)));
        }
        if (result.type == OffsetType.FETCH) {
          consumer.seek(result.topicPartition, result.offset);
        }
        consumer.resume(singletonList(result.topicPartition));
      });
    }

    ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ZERO);
    consumer.pause(consumerRecords.partitions());
    for (TopicPartition p : consumerRecords.partitions()) {
      getHandler(p).handleAsync(p, consumerRecords.records(p));
    }
  }

  private PartitionHandler getHandler(TopicPartition p) throws ExecutionException {
    PartitionHandler handler = handlers.get(p.partition());
    if (handler.isRunning()) {
      return handler;
    }
    // in case this handler is not running discard it and let cache to load a new one
    handlers.invalidate(p.partition());
    return handlers.get(p.partition());
  }

  @Override
  protected Scheduler scheduler() {
    return newFixedDelaySchedule(setup.pollInterval().toNanos(), setup.pollInterval().toNanos(), NANOSECONDS);
  }

  @Override
  protected void startUp() {
    consumer.subscribe(singletonList(topic().value()),
        new ConsumerRebalanceListener() {
          @Override public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            stopHandlersSync();
          }

          @Override public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            partitionsAssigned = !partitions.isEmpty();
          }
        });
  }

  @Override
  protected void shutDown() {
    consumer.pause(consumer.assignment());
    stopHandlersSync();
    consumer.close();
    partitionsAssigned = false;
  }

  @Override
  protected String serviceName() {
    return String.format("[%s]%s<%s>", groupId, clientId, topic().value());
  }

  private void stopHandlersSync() {
    // sync wait for all handlers to gracefully shut down(finish current records) and commit offset
    handlers.asMap().values().forEach(handler -> {
      handler.stopAsync();// will triggerShutdown() to finish current records
      handler.getResult().ifPresent(result -> {
        if (result.type == OffsetType.COMMIT) {
          consumer.commitSync(ImmutableMap.of(result.topicPartition, new OffsetAndMetadata(result.offset)));
        }
      });
    });
    handlers.invalidateAll();
  }

  @Immutable
  public interface Setup {

    Topic topic();

    Type type();

    Optional<String> group();

    @Default
    default int maxPollRecords() {
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

    class Builder extends ImmutableDispatcher.Setup.Builder {}
  }

  private enum OffsetType {
    COMMIT, FETCH
  }

  private final class PartitionHandler extends AbstractExecutionThreadService {
    private final Receiver<R> receiver;
    private final BlockingQueue<KafkaRecords> input = new LinkedBlockingQueue<>(1);
    private final BlockingQueue<Result> output = new LinkedBlockingQueue<>(1);
    private final Integer partition;

    PartitionHandler(Integer partition) {
      this.partition = partition;
      this.receiver = receiverSupplier.get();
    }

    private void handleAsync(TopicPartition p, List<ConsumerRecord<String, String>> records) {
      input.add(new KafkaRecords(p, records));
    }

    private Optional<Result> getResult() {
      return Optional.ofNullable(output.poll());
    }

    @Override
    protected void run() throws Exception {
      while (isRunning()) {
        KafkaRecords records = input.take();
        synchronized (input) {
          try {
            receiver.on(records);
            if (setup.autoCommit()) {
              records.commit();
            }
          } catch (Exception e) {
            // current partition handler is going to die and new one will be recreated on next iteration
            // need to reset fetch offset to process same records on next iteration
            // overall it can cause to have infinite loop of processing poison record
            output.put(new Result(records.partition, records.firstOffset, OffsetType.FETCH));
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
      synchronized (input) {
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
      return String.format("%s:Handler<shard-%d>", Dispatcher.this.serviceName(), partition);
    }

    private final class Result {
      private TopicPartition topicPartition;
      private Long offset;
      private OffsetType type;

      Result(TopicPartition topicPartition, Long offset, OffsetType type) {
        this.topicPartition = topicPartition;
        this.offset = offset;
        this.type = type;
      }
    }

    private final class KafkaRecords implements Receiver.Records<R> {

      private final long lastOffset;
      private final long firstOffset;
      private final TopicPartition partition;
      private final List<ConsumerRecord<String, String>> records;

      private boolean committed;

      KafkaRecords(TopicPartition partition, List<ConsumerRecord<String, String>> records) {
        this.records = records;
        this.partition = partition;
        this.firstOffset = records.get(0).offset();
        this.lastOffset = records.get(records.size() - 1).offset();
      }

      @Override
      public Iterator<R> iterator() {
        return Iterators.transform(records.iterator(), r -> decode(r.value()));
      }

      private @Nullable R decode(@Nullable String value) {
        // nulls can occur as tombstone records etc
        return value == null ? null : json.fromJson(value, codec);
      }

      @Override
      public Topic topic() {
        return Topic.of(partition.topic());
      }

      @Override
      public int shard() {
        return partition.partition();
      }

      @Override
      public int size() {
        return records.size();
      }

      @Override
      public void commit() {
        if (!committed) {
          try {
            output.put(new Result(partition, lastOffset + 1, OffsetType.COMMIT));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          committed = true;
        }
      }

      @Override public String toString() {
        return toStringHelper(this)
            .add("topic", partition.topic())
            .add("partition", partition.partition())
            .add("size", size())
            .add("firstOffset", firstOffset)
            .add("lastOffset", lastOffset)
            .toString();
      }
    }
  }
}
