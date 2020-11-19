package io.immutables.micro.stream.http.kafka;

import io.immutables.micro.ExceptionSink;
import io.immutables.micro.stream.http.BrokerApi;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.*;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import static com.google.common.util.concurrent.Service.State.FAILED;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.CLIENT_ID_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

@Enclosing
public class KafkaBroker implements BrokerApi, AutoCloseable {

  private final ExceptionSink sink;
  private final Producer<String, String> kafkaProducer;
  private final LoadingCache<ClientId, ConsumerHandler> handlers;

  @Inject
  public KafkaBroker(KafkaHttpModule.BrokerInfo brokerInfo, Setup setup, ExceptionSink sink) {
    this.sink = sink;
    this.kafkaProducer = new KafkaProducer<>(ImmutableMap.of(
        BOOTSTRAP_SERVERS_CONFIG, brokerInfo.connect(),
        CLIENT_ID_CONFIG, "KafkaSender_" + System.identityHashCode(this),
        KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
    ));
    this.handlers = CacheBuilder.newBuilder()
        .expireAfterAccess(setup.idleReceiverTimeout())
        .removalListener(new RemovalListener<ClientId, ConsumerHandler>() {
          @Override
          public void onRemoval(RemovalNotification<ClientId, ConsumerHandler> notification) {
            ConsumerHandler handler = notification.getValue();
            if (handler.state() == FAILED) {
              sink.unhandled(handler.failureCause());
            }
            handler.stopAsync();
          }
        })
        .build(new CacheLoader<ClientId, ConsumerHandler>() {
          @Override
          public ConsumerHandler load(ClientId clientId) {
            ConsumerHandler consumerHandler = new ConsumerHandler(brokerInfo.connect(), setup, clientId, sink);
            consumerHandler.startAsync().awaitRunning();
            return consumerHandler;
          }
        });
  }

  @Override
  public DispatcherResponse poll(DispatcherRequest request) throws ExecutionException {
    ConsumerHandler consumer = handlers.get(request.clientId());
    consumer.handleProcessedRecords(request.offsets());
    return DispatcherResponse.with(consumer.getNextRecords());
  }

  @Override
  public void unsubscribe(DispatcherRequest request) throws ExecutionException {
    ConsumerHandler consumerHandler = handlers.get(request.clientId());
    consumerHandler.handleProcessedRecords(request.offsets());
    handlers.invalidate(request.clientId());
  }

  @Value.Immutable
  public interface Setup {

    @Default
    default int maxPollRecords() {
      return 10;
    }

    @Default
    default Duration idleReceiverTimeout() {
      return Duration.ofMinutes(5);
    }

    class Builder extends ImmutableKafkaBroker.Setup.Builder {}
  }

  @Override
  public void publish(PublisherRequest request) {
    request.records().stream()
        .map(r -> new ProducerRecord<>(request.topic(), encodeShard(r, request.topic()), r.key(), r.value()))
        .forEach(r ->
            kafkaProducer.send(r, (metadata, e) -> { if (e != null) sink.unhandled(e); }));
  }

  /**
   * If record has sharded key, use it to calculate shard Otherwise a default kafka strategy will be used: if a key is
   * present choose a partition based on a hash of the key else choose a partition in a round-robin fashion
   */
  private Integer encodeShard(Record record, String topic) {
    if (record.shardKey() == null) {
      return null;
    }
    int partitions = kafkaProducer.partitionsFor(topic).size();
    return Math.abs(Objects.hashCode(record.shardKey())) % partitions;
  }

  @Override
  public void close() {
    handlers.invalidateAll();
    kafkaProducer.close();
  }
}
