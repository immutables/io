package io.immutables.micro.wiring.docker;

import java.nio.file.Files;
import java.nio.file.Paths;

@Deprecated
// TODO need to revise / refactor this crap
public class DockerRunner {
  private static final String COCKROACH_DB_IMAGE = "cockroachdb/cockroach:v19.1.2";
  private static final String KAFKA_IMAGE = "wurstmeister/kafka:2.11-2.0.0";
  private static final int ZOOKEEPER_PORT = 2181;
  private static final String ZOOKEEPER_IMAGE = "wurstmeister/zookeeper:3.4.6";
  private static final String ZOOKEEPER_SERVICE_NAME = "Zookeeper";
  private static final String ROACH_SERVICE_NAME = "CockroachDB";
  private static final String KAFKA_SERVICE_NAME = "Kafka";

  private static final Container ROACH_CONTAINER = new Container.Builder()
      .name(context -> "roach-on-" + context.externalPort())
      .serviceName(ROACH_SERVICE_NAME)
      .image(COCKROACH_DB_IMAGE)
      .addExposedPort(context -> ExposedPort.of(context.externalPort(), context.externalPort()))
      .addExposedPort(context -> ExposedPort.of(context.randomPortSupplier().get(), 8080))
      .cmd(context -> "start --insecure --listen-addr=0.0.0.0:" + context.externalPort())
      .build();

  private static final Container ZOOKEEPER_CONTAINER = new Container.Builder()
      .name(context -> "zookeeper-on-" + context.externalPort())
      .serviceName(ZOOKEEPER_SERVICE_NAME)
      .image(ZOOKEEPER_IMAGE)
      .addExposedPort(context -> ExposedPort.of(context.externalPort(), context.externalPort()))
      .addToNetwork("kafka-network")
      .build();

  private static final Container KAFKA_CONTAINER = new Container.Builder()
      .name(context -> "kafka-on-" + context.externalPort())
      .serviceName(KAFKA_SERVICE_NAME)
      .image(KAFKA_IMAGE)
      .addToNetwork("kafka-network")
      .addExposedPort(context -> ExposedPort.of(context.externalPort(), context.externalPort()))
      .addEnvironmentVariable(context -> EnvironmentVariable.of(
          "KAFKA_ADVERTISED_LISTENERS",
          "PLAINTEXT://" + (context.isLocalhostAvailable() ? "localhost" : "kafka-on-" + context.externalPort()) +
              ":" + context.externalPort()))
      .addEnvironmentVariable(context -> EnvironmentVariable.of("KAFKA_LISTENERS",
          "PLAINTEXT://0.0.0.0:" + context.externalPort()))
      .addEnvironmentVariable(context -> EnvironmentVariable.of("KAFKA_ZOOKEEPER_CONNECT",
          context.serviceHostnameResolver().apply(ZOOKEEPER_SERVICE_NAME) + ":2181"))
      .addEnvironmentVariable(context -> EnvironmentVariable.of("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false"))
      .build();

  private static BaseDockerRunner envSpecificDockerRunner;
  static {
    /*
     * Docker creates a /.dockerenv file automatically.
     * So if this file is present then we are in the docker container
     */
    if (Files.exists(Paths.get("/.dockerenv"))) {
      System.out.println("We are in docker");
      envSpecificDockerRunner = new ContainerDockerRunner();
    } else {
      envSpecificDockerRunner = new LocalDockerRunner();
    }
  }
  @Deprecated
  public static String assertPostgresRunning(int port) {
    envSpecificDockerRunner.assertRunning(ROACH_CONTAINER, port);
    return "localhost";
  }

  @Deprecated
  public static String assertRoachRunning(int port) {
    envSpecificDockerRunner.assertRunning(ROACH_CONTAINER, port);
    return envSpecificDockerRunner.getServiceHostname(ROACH_SERVICE_NAME);
  }

  public static String assertKafkaIsRunning(int kafkaPort) {
    envSpecificDockerRunner.assertRunning(ZOOKEEPER_CONTAINER, ZOOKEEPER_PORT);
    envSpecificDockerRunner.assertRunning(KAFKA_CONTAINER, kafkaPort);
    return envSpecificDockerRunner.getServiceHostname(KAFKA_SERVICE_NAME);
  }

  public static void killKafka() {
    envSpecificDockerRunner.stop(KAFKA_SERVICE_NAME);
    envSpecificDockerRunner.stop(ZOOKEEPER_SERVICE_NAME);
  }
}
