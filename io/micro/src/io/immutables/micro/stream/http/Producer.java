package io.immutables.micro.stream.http;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.stream.Keyed;
import io.immutables.stream.Sender;
import io.immutables.stream.Sharded;
import io.immutables.stream.Topic;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import javax.annotation.Nullable;
import com.google.common.reflect.TypeToken;
import org.immutables.value.Value;
import static com.google.common.collect.Streams.stream;
import static java.util.stream.Collectors.toList;

public class Producer<R> implements Sender<R> {

  @Value.Immutable
  public interface Setup {
    Topic topic();

    Type type();

    static Setup of(Topic topic, Type type) {
      return ImmutableSetup.builder().topic(topic).type(type).build();
    }
  }

  private final BrokerApi brokerApi;

  private final Codec<R> valueCodec;
  private final @Nullable Codec<Object> keyCodec;
  private final @Nullable Codec<Object> shardKeyCodec;
  private final boolean keyed;
  private final boolean sharded;
  private final OkJson json;
  private final Setup setup;

  @SuppressWarnings("unchecked")
  public Producer(BrokerApi brokerApi, OkJson json, Setup setup) {
    this.json = json;
    this.setup = setup;
    this.brokerApi = brokerApi;
    this.valueCodec = (Codec<R>) json.get(TypeToken.of(setup.type()));
    this.keyCodec = getKeyCodec(json, (Class<?>) setup.type());
    this.shardKeyCodec = getShardKeyAdapter(json, (Class<?>) setup.type());
    this.keyed = keyCodec != null;
    this.sharded = shardKeyCodec != null;
  }

  @Override
  public void write(Iterable<R> records) {
    brokerApi.publish(BrokerApi.PublisherRequest.with(
        setup.topic().value(),
        stream(records).map(r -> BrokerApi.Record.of(
            encodeValue(r),
            encodeKey(r),
            encodeShardKey(r))).collect(toList())));
  }

  private @Nullable String encodeShardKey(R record) {
    Object shardKey = sharded ? ((Sharded<?>) record).shardKey() : null;
    return shardKey == null ? null : json.toJson(shardKey, shardKeyCodec);
  }

  private @Nullable String encodeKey(R record) {
    Object key = keyed ? ((Keyed<?>) record).key() : null;
    return key == null ? null : json.toJson(key, keyCodec);
  }

  private String encodeValue(R record) {
    return json.toJson(record, valueCodec);
  }

  @SuppressWarnings("unchecked")
  private @Nullable Codec<Object> getKeyCodec(OkJson json, Class<?> type) {
    if (!Keyed.class.isAssignableFrom(type)) {
      return null;
    }

    Class<?> keyType = Arrays.stream((type).getMethods())
        .filter(m -> !m.isBridge())
        .filter(m -> m.getName().equals("key"))
        .findFirst()
        .map(Method::getReturnType)
        .orElseThrow(AssertionError::new);

    return (Codec<Object>) json.get(TypeToken.of(keyType));
  }

  @SuppressWarnings("unchecked")
  private @Nullable Codec<Object> getShardKeyAdapter(OkJson json, Class<?> type) {
    if (!Sharded.class.isAssignableFrom(type)) {
      return null;
    }

    Class<?> keyType = Arrays.stream(type.getMethods())
        .filter(m -> !m.isBridge())
        .filter(m -> m.getName().equals("shardKey"))
        .findFirst()
        .map(Method::getReturnType)
        .orElseThrow(AssertionError::new);

    return (Codec<Object>) json.get(TypeToken.of(keyType));
  }

  @Override
  public Topic topic() {
    return setup.topic();
  }
}
