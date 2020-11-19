package io.immutables.micro.kafka;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.stream.Keyed;
import io.immutables.stream.Sender;
import io.immutables.stream.Sharded;
import io.immutables.stream.Topic;
import io.immutables.micro.ExceptionSink;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import javax.annotation.Nullable;
import com.google.common.reflect.TypeToken;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.CLIENT_ID_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

@Enclosing
public class KafkaSender<R> implements Sender<R>, AutoCloseable {

  private final OkJson json;
  private final ExceptionSink sink;
  private final Setup setup;
  private final Producer<String, String> kafkaProducer;

  private final Codec<R> codec;
  private final Codec<Object> keyCodec;
  private final boolean keyed;
  private final boolean sharded;

  public KafkaSender(KafkaModule.BrokerInfo brokerInfo, OkJson json, ExceptionSink sink, Setup setup) {
    this.json = json;
    this.sink = sink;
    this.setup = setup;
    this.kafkaProducer = createProducer(brokerInfo.connect(), setup);
    this.codec = json.get(setup.type());
    this.keyCodec = getKeyCodec(json, (Class<?>) setup.type());
    this.keyed = keyCodec != null;
    this.sharded = Sharded.class.isAssignableFrom((Class<?>) setup.type());
  }

  private KafkaProducer<String, String> createProducer(String host, Setup setup) {
    Properties props = new Properties();
    props.put(BOOTSTRAP_SERVERS_CONFIG, host);
    props.put(CLIENT_ID_CONFIG, "KafkaSender@" + setup.type().getTypeName() + "_" + System.identityHashCode(this));
    props.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new KafkaProducer<>(props);
  }

  @Override
  public Topic topic() {
    return setup.topic();
  }

  @Override
  public void write(Iterable<R> records) {
    for (R record : records) {
      kafkaProducer.send(getRecord(record), (metadata, e) -> { if (e != null) sink.unhandled(e); });
    }
  }

  private ProducerRecord<String, String> getRecord(R record) {
    return new ProducerRecord<>(
        topic().value(),
        sharded ? encodeShard(record) : null,
        keyed ? encodeKey(record) : null,
        encodeValue(record));
  }

  /**
   * If record has sharded key, use it to calculate shard Otherwise a default kafka strategy will be used: if a key is
   * present choose a partition based on a hash of the key else choose a partition in a round-robin fashion
   */
  private int encodeShard(@Nullable R record) {
    Sharded<?> sharded = (Sharded<?>) record;
    // FIXME Does this calls for cluster for each record
    int partitions = kafkaProducer.partitionsFor(topic().value()).size();
    return (record == null ? 0 : Math.abs(Objects.hashCode(sharded.shardKey()))) % partitions;
  }

  private @Nullable String encodeKey(@Nullable R record) {
    Keyed<?> keyed = (Keyed<?>) record;
    return record == null || keyed.key() == null ? null : json.toJson(keyed.key(), keyCodec);
  }

  private @Nullable String encodeValue(@Nullable R record) {
    return record == null ? null : json.toJson(record, codec);
  }

  @Override
  public void close() {
    kafkaProducer.close();
  }

  @SuppressWarnings("unchecked")
  private @Nullable Codec<Object> getKeyCodec(OkJson json, Class<?> type) {
    if (!Keyed.class.isAssignableFrom(type)) return null;

    Class<?> keyType = Arrays.stream((type).getMethods())
        .filter(m -> !m.isBridge())
        .filter(m -> m.getName().equals("key"))
        .findFirst()
        .map(Method::getReturnType)
        .orElseThrow(AssertionError::new);

    return (Codec<Object>) json.get(TypeToken.of(keyType));
  }

  @Immutable
  public interface Setup {
    Topic topic();

    Type type();

    class Builder extends ImmutableKafkaSender.Setup.Builder {}
  }
}
