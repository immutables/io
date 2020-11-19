package io.immutables.micro.stream.http.kafka;

import io.immutables.codec.OkJson;
import io.immutables.stream.Receiver;
import io.immutables.stream.Sender;
import io.immutables.stream.Topic;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Jaxrs;
import io.immutables.micro.Streams;
import io.immutables.micro.Streams.DispatcherFactory;
import io.immutables.micro.Streams.SenderFactory;
import io.immutables.micro.Streams.TopicImport;
import io.immutables.micro.Systems.Shared;
import io.immutables.micro.kafka.KafkaAdmin;
import io.immutables.micro.stream.http.BrokerApi;
import io.immutables.micro.stream.http.Dispatcher;
import io.immutables.micro.stream.http.Producer;
import io.immutables.micro.wiring.LocalPorts;
import io.immutables.micro.wiring.docker.DockerRunner;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.immutables.value.Value;
import static java.util.stream.Collectors.toList;
import static io.immutables.micro.Streams.Setup.Mode.CREATE_AND_DROP;
import static io.immutables.micro.Streams.Setup.Mode.CREATE_IF_NOT_EXIST;
import static io.immutables.micro.Streams.topicFor;

@Value.Enclosing
public class KafkaHttpModule implements Module {

  public static final int KAFKA_STANDARD_PORT = 9092;

  @Override
  public void configure(Binder binder) {
    // topics can be used many times by many servicelets so we permit duplicates
    Multibinder.newSetBinder(binder, TopicImport.class).permitDuplicates();
    binder.bind(Key.get(BrokerApi.class))
        .toProvider(Jaxrs.proxyProvider(Key.get(BrokerApi.class)))
        .in(Scopes.SINGLETON);
  }

  @Provides
  @Singleton
  public BrokerInfo brokerInfo(Streams.Setup setup) {
    HostAndPort hostPort = normalizeHostPort(setup);
    if (setup.autostart()) {
      String kafkaHost = DockerRunner.assertKafkaIsRunning(hostPort.getPort());
      hostPort = HostAndPort.fromParts(kafkaHost, hostPort.getPort());
    }
    return new BrokerInfo.Builder()
        .setup(setup)
        .hostPort(hostPort)
        .build();
  }

  @Provides
  public @Shared BrokerInfo brokerInfo(BrokerInfo brokerInfo) {
    return brokerInfo;
  }

  private HostAndPort normalizeHostPort(Streams.Setup setup) {
    HostAndPort hostPort = setup.hostPort();
    if (LocalPorts.isLocalhost(hostPort.getHost())) {
      int port = hostPort.getPortOrDefault(KAFKA_STANDARD_PORT);
      if (port == 0) {
        port = LocalPorts.findSomeFreePort();
      }
      return HostAndPort.fromParts(hostPort.getHost(), port);
    }
    return hostPort;
  }

  @Value.Immutable
  public interface BrokerInfo {
    /** Reference to the configuration. */
    Streams.Setup setup();

    /** Actual host/port that will be used to connect to broker. Port might be absent. */
    HostAndPort hostPort();

    /** Host with optional port. */
    default String connect() {
      return hostPort().toString();
    }

    class Builder extends ImmutableKafkaHttpModule.BrokerInfo.Builder {}
  }

  public static class KafkaManager extends AbstractIdleService {

    private final BrokerInfo brokerInfo;
    private final List<Topic> topics;
    private final Streams.Setup.Mode topicMode;

    public KafkaManager(BrokerInfo brokerInfo, Set<TopicImport> topics) {
      this.brokerInfo = brokerInfo;
      this.topics = topics.stream().map(t -> topicFor(t.key())).collect(toList());
      this.topicMode = brokerInfo.setup().topicMode();
    }

    @Override
    protected void startUp() {
      if (topicMode == CREATE_AND_DROP || topicMode == CREATE_IF_NOT_EXIST) {
        topics.forEach(topic -> KafkaAdmin.createIfNotExists(brokerInfo.connect(), topic, brokerInfo.setup().shards()));
      }
    }

    @Override
    protected void shutDown() {
      if (topicMode == CREATE_AND_DROP) {
        topics.forEach(t -> KafkaAdmin.deleteTopic(brokerInfo.connect(), t));
      }
    }
  }

  @Provides
  @Singleton
  public KafkaBroker.Setup setup() {
    return new KafkaBroker.Setup.Builder()
        .idleReceiverTimeout(Duration.ofMinutes(5))
        .maxPollRecords(10)
        .build();
  }

  @Provides
  public @Shared KafkaBroker.Setup setup(KafkaBroker.Setup setup) {
    return setup;
  }

  @Provides
  @Singleton
  public KafkaManager kafkaManager(BrokerInfo brokerInfo, Set<TopicImport> usedTopics) {
    return new KafkaManager(brokerInfo, usedTopics);
  }

  @ProvidesIntoSet
  public Service asService(KafkaManager manager) {
    return manager;
  }

  @Provides
  @Singleton
  public SenderFactory senderProvider(Injector injector, OkJson json) {
    return new SenderFactory() {
      @Override public <R> Sender<R> create(Key<R> key) {
        BrokerApi brokerApi = injector.getInstance(BrokerApi.class);
        return new Producer<>(brokerApi, json, Producer.Setup.of(topicFor(key), key.getTypeLiteral().getType()));
      }
    };
  }

  @Provides
  @Singleton
  public DispatcherFactory dispatcherFactory(Injector injector, OkJson json, ExceptionSink sink) {
    return new DispatcherFactory() {
      @Override public <R> Service create(Key<R> key, Optional<String> group, Provider<Receiver<R>> receiver) {
        BrokerApi brokerApi = injector.getInstance(BrokerApi.class);
        return new Dispatcher<>(brokerApi, sink, json, receiver::get,
            new Dispatcher.Setup.Builder()
                .group(group)
                .topic(topicFor(key))
                .type(key.getTypeLiteral().getType())
                .pollInterval(Duration.ofMillis(20))
                .idleReceiverTimeout(Duration.ofMinutes(5))
                .autoCommit(true)
                .build());
      }
    };
  }

  @Provides
  public @Shared DispatcherFactory sharedDispatcherFactory(DispatcherFactory dispatcherFactory) {
    return dispatcherFactory;
  }

  @Provides
  public @Shared SenderFactory sharedSenderFactory(SenderFactory senderFactory) {
    return senderFactory;
  }
}
