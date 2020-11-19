package io.immutables.micro.creek;

import io.immutables.codec.OkJson;
import io.immutables.stream.Topic;
import org.junit.Test;

public class ProducerTest {
  public static class BadHashEntity {
    public String id;

    BadHashEntity(String id) {
      this.id = id;
    }

    @Override
    public int hashCode() {
      return -3;
    }
  }

  @Test
  public void canWriteValueWithBadHash() {
    Topic A = Topic.of("A");

    Broker broker = new Broker(() -> 0, 1, 1);
    broker.create(A, 2);

    Producer.Conf conf = new Producer.Conf.Builder()
        .topic(A)
        .type(BadHashEntity.class)
        .build();

    Producer<BadHashEntity> producer = new Producer<>(broker, new OkJson(), conf);

    producer.write(new BadHashEntity("record"));
  }
}
