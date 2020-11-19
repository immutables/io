package io.immutables.micro.stream.http.kafka;

import io.immutables.micro.ExceptionSink;
import io.immutables.micro.stream.http.BrokerApi.ClientId;
import io.immutables.micro.stream.http.BrokerApi.Record;
import io.immutables.micro.stream.http.BrokerApi.Records;
import io.immutables.micro.stream.http.BrokerApi.ShardOffset;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import static com.google.common.collect.ImmutableMap.of;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST;

class ConsumerHandler extends AbstractExecutionThreadService {

  private final BlockingQueue<ShardOffset> processedOffsets = new LinkedBlockingQueue<>();
  private final BlockingQueue<List<Records>> nextRecords = new SynchronousQueue<>();

  private final ClientId clientId;
  private final Consumer<String, String> consumer;

  ConsumerHandler(String host, KafkaBroker.Setup setup, ClientId clientId, ExceptionSink exceptionSink) {
    this.clientId = clientId;
    consumer = createConsumer(host, setup, clientId.group(), clientId.id());
    consumer.subscribe(singletonList(clientId.topic()), new ConsumerRebalanceListener() {
      @Override public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        if (!partitions.isEmpty()) {
          int paused = consumer.paused().size();
          if (paused == 0) {
            return;
          }
          synchronized (processedOffsets) {
            while (processedOffsets.size() < paused) {
              try {
                processedOffsets.wait();
              } catch (InterruptedException e) {
                exceptionSink.unhandled(e);
              }
            }
          }
          commitResultsIfAny();
        }
      }

      @Override public void onPartitionsAssigned(Collection<TopicPartition> partitions) {}
    });
  }

  @Override
  protected String serviceName() {
    return String.format("%s:%s<%s>", clientId.group(), clientId.id(), clientId.topic());
  }

  void handleProcessedRecords(List<ShardOffset> offsets) {
    if (!offsets.isEmpty()) {
      synchronized (processedOffsets) {
        processedOffsets.addAll(offsets);
        processedOffsets.notify();
      }
    }
  }

  List<Records> getNextRecords() {
    return ofNullable(nextRecords.poll()).orElse(new ArrayList<>());
  }

  @Override protected void triggerShutdown() {
    commitResultsIfAny();
    consumer.close();
  }

  @Override protected void run() throws Exception {
    while (isRunning()) {
      commitResultsIfAny();
      ConsumerRecords<String, String> records = consumer.poll(Duration.ZERO);
      consumer.pause(records.partitions());
      nextRecords.put(toShardRecords(records));
    }
  }

  private void commitResultsIfAny() {
    ShardOffset o = processedOffsets.poll();
    while (o != null) {
      TopicPartition partition = new TopicPartition(clientId.topic(), o.shard());
      consumer.commitSync(of(partition, new OffsetAndMetadata(o.offset() + 1)));
      consumer.seek(partition, new OffsetAndMetadata(o.offset() + 1));
      consumer.resume(singletonList(partition));
      o = processedOffsets.poll();
    }
  }

  private List<Records> toShardRecords(ConsumerRecords<String, String> recordsPerShard) {
    return recordsPerShard.partitions().stream()
        .map(shard -> toRecords(shard.partition(), recordsPerShard.records(shard)))
        .collect(toList());
  }

  private Records toRecords(int shard, List<ConsumerRecord<String, String>> records) {
    long lastOffset = records.get(records.size() - 1).offset();
    return Records.of(
        ShardOffset.of(shard, lastOffset),
        records.stream().map(r -> Record.of(r.value())).collect(toList()));
  }

  private Consumer<String, String> createConsumer(String host, KafkaBroker.Setup setup, Optional<String> group,
      String clientId) {
    Properties props = new Properties();
    props.put(BOOTSTRAP_SERVERS_CONFIG, host);
    props.put(GROUP_ID_CONFIG, group.orElse("Group_" + System.identityHashCode(this)));
    props.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(MAX_POLL_RECORDS_CONFIG, setup.maxPollRecords());
    props.put(CONNECTIONS_MAX_IDLE_MS_CONFIG, setup.idleReceiverTimeout().toMillis());
    props.put(ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    props.put(AUTO_OFFSET_RESET_CONFIG, EARLIEST.name().toLowerCase());
    props.put(CLIENT_ID_CONFIG, clientId + "_" + System.identityHashCode(this));
    return new KafkaConsumer<>(props);
  }
}
