package io.immutables.micro.stream.http;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.immutables.data.Data;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Data
@Enclosing
@Path("/")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public interface BrokerApi {

  @POST
  @Path("/publish")
  void publish(PublisherRequest request);

  @POST
  @Path("/poll")
  DispatcherResponse poll(DispatcherRequest request) throws Exception;

  @POST
  @Path("/unsubscribe")
  void unsubscribe(DispatcherRequest request) throws Exception;

  @Immutable
  interface PublisherRequest {
    String topic();

    List<Record> records();

    static PublisherRequest with(String topic, List<Record> records) {
      return ImmutableBrokerApi.PublisherRequest.builder().topic(topic).records(records).build();
    }
  }

  @Immutable
  interface Record {
    String value();

    @Nullable
    String key();

    @Nullable
    String shardKey();

    static Record of(String value, @Nullable String key, @Nullable String shardKey) {
      return ImmutableBrokerApi.Record.builder().value(value).key(key).shardKey(shardKey).build();
    }

    static Record of(String value) {
      return ImmutableBrokerApi.Record.builder().value(value).build();
    }
  }

  @Immutable
  interface DispatcherRequest {
    ClientId clientId();

    List<ShardOffset> offsets();

    static DispatcherRequest with(ClientId clientId, List<ShardOffset> offsets) {
      return ImmutableBrokerApi.DispatcherRequest.builder().clientId(clientId).offsets(offsets).build();
    }
  }

  @Immutable
  interface ShardOffset {
    int shard();

    long offset();

    static ShardOffset of(int shard, long offset) {
      return ImmutableBrokerApi.ShardOffset.builder().shard(shard).offset(offset).build();
    }
  }

  @Immutable
  interface ClientId {
    String id();

    Optional<String> group();

    String topic();

    static ClientId clientId(String id, Optional<String> group, String topic) {
      return ImmutableBrokerApi.ClientId.builder().id(id).group(group).topic(topic).build();
    }
  }

  @Immutable
  interface DispatcherResponse {

    List<Records> records();

    static DispatcherResponse with(List<Records> records) {
      return ImmutableBrokerApi.DispatcherResponse.builder().records(records).build();
    }
  }

  @Immutable
  interface Records {

    List<Record> records();

    ShardOffset shardOffset();

    default int shard() { return shardOffset().shard(); }

    default long offset() { return shardOffset().offset(); }

    default int size() { return records().size(); }

    static Records of(ShardOffset shardOffset, List<Record> records) {
      return ImmutableBrokerApi.Records.builder().records(records).shardOffset(shardOffset).build();
    }
  }
}
