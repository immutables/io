package io.immutables.micro.creek;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.stream.Keyed;
import io.immutables.stream.Sender;
import io.immutables.stream.Sharded;
import io.immutables.stream.Topic;
import io.immutables.micro.creek.Broker.Publication;
import io.immutables.micro.creek.Broker.Record;
import java.lang.reflect.Type;
import java.util.Objects;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;

/**
 * Rudimentary implementation or JSON encoding and shard routing when sending records.
 */
@Enclosing
public class Producer<R> implements Sender<R> {

  @Immutable
  public interface Conf {
    Topic topic();

    Type type();

    class Builder extends ImmutableProducer.Conf.Builder {}
  }

  private final Broker broker;
  private final Conf conf;
  private final Codec<R> codec;
  private final OkJson json;

  @SuppressWarnings("unchecked") // safe unchecked via reflective type check
  public Producer(Broker broker, OkJson json, Conf conf) {
    this.broker = broker;
    this.json = json;
    this.codec = (Codec<R>) json.get(TypeToken.of(conf.type()));
    this.conf = conf;
  }

  @Override
  public Topic topic() {
    return conf.topic();
  }

  @Override
  public void write(Iterable<R> iterable) {
    Publication publication = broker.publish(conf.topic());
    // triggering early transformation, dunno if this is the best way to handle this,
    // but any encoding errors will be thrown before going into broker
    ImmutableList<Record> records = ImmutableList.copyOf(
        Iterables.transform(iterable, v -> toRecord(publication, v)));

    publication.write(records);
  }

  private Broker.Record toRecord(Broker.Publication publication, R record) {
    return broker.record(
        selectShard(publication, record),
        encodeKey(record),
        encodeValue(record));
  }

  private @Nullable Object encodeKey(@Nullable Object record) {
    Object key = record instanceof Keyed<?> ? ((Keyed<?>) record).key() : null;
    return key == null ? null : json.toJson(key);
  }

  private @Nullable Object encodeValue(@Nullable R record) {
    return record == null ? null : json.toJson(record, codec);
  }

  private int selectShard(Broker.Publication publication, @Nullable Object value) {
    Object shardingKey = value instanceof Sharded<?>
        ? ((Sharded<?>) value).shardKey()
        : value;

    return Math.abs(Objects.hashCode(shardingKey)) % publication.shards();
  }
}
