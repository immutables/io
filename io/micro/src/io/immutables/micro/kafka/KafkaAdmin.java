package io.immutables.micro.kafka;

import io.immutables.stream.Topic;
import java.util.concurrent.ExecutionException;
import com.google.common.collect.ImmutableMap;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Collections.singleton;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;

public class KafkaAdmin {

  private static final short REPLICATION_FACTOR = 1;

  public static void create(String host, Topic topic, int shards) {
    create(host, topic, false, shards);
  }

  public static void createIfNotExists(String host, Topic topic, int shards) {
    create(host, topic, true, shards);
  }

  private static void create(String host, Topic topic, boolean needCheck, int shards) {
    try (AdminClient admin = AdminClient.create(ImmutableMap.of(BOOTSTRAP_SERVERS_CONFIG, host))) {
      if (!needCheck || !topicExist(topic, admin)) {
        admin.createTopics(singleton(new NewTopic(topic.value(), shards, REPLICATION_FACTOR))).values().get(topic.value()).get();
        Await.await(5, SECONDS, 50).until(() -> topicExist(topic, admin));
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private static boolean topicExist(Topic topic, AdminClient admin) {
    try {
      return admin.listTopics().names().get().contains(topic.value());
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public static void deleteTopic(String host, Topic topic) {
    try (AdminClient admin = AdminClient.create(ImmutableMap.of(BOOTSTRAP_SERVERS_CONFIG, host))) {
      admin.deleteTopics(singleton(topic.value())).all().get();
    } catch (InterruptedException | ExecutionException e) {
      if (!(e.getCause() instanceof UnknownTopicOrPartitionException)) {
        throw new RuntimeException(e.getMessage(), e);
      }
    }
  }
}
