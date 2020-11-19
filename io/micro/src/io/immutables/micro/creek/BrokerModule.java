package io.immutables.micro.creek;

import io.immutables.codec.OkJson;
import io.immutables.stream.Receiver;
import io.immutables.stream.Sender;
import io.immutables.micro.Streams;
import io.immutables.micro.Streams.TopicImport;
import io.immutables.micro.Systems.Shared;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import static io.immutables.micro.Streams.topicFor;

/** This is test/simulation implementation. Real Kafka modules are not required to share. */
public class BrokerModule implements Module {

  @Override
  public void configure(Binder binder) {
    // topics can be used many times by many servicelets so we permit duplicates
    Multibinder.newSetBinder(binder, TopicImport.class).permitDuplicates();
  }

  @Provides
  @Singleton
  public Broker broker(Set<TopicImport> usedTopics, Streams.Setup setup) {
    Broker broker = new Broker(
        System::currentTimeMillis,
        TimeUnit.MINUTES.toMillis(5),
        TimeUnit.SECONDS.toMillis(30));

    for (TopicImport t : usedTopics) {
      broker.create(topicFor(t.key()), setup.shards());
    }
    return broker;
  }

  @Provides
  public @Shared Broker sharedBroker(Broker broker) {
    return broker;
  }

  @Provides
  @Singleton
  public Streams.SenderFactory senderFactory(Broker broker, OkJson json) {
    return new Streams.SenderFactory() {
      @Override public <R> Sender<R> create(Key<R> key) {
        return new Producer<>(
            broker,
            json,
            new Producer.Conf.Builder()
                .topic(topicFor(key))
                .type(key.getTypeLiteral().getType())
                .build());
      }
    };
  }

  @Provides
  public @Shared Streams.SenderFactory sharedSenderFactory(Streams.SenderFactory senderFactory) {
    return senderFactory;
  }

  @Provides
  @Singleton
  public Streams.DispatcherFactory dispatcherFactory(Broker broker, OkJson json) {
    return new Streams.DispatcherFactory() {
      @Override
      public <R> Service create(Key<R> key, Optional<String> group, Provider<Receiver<R>> receiverProvider) {
        return new Dispatcher<>(broker, json, receiverProvider::get,
            new Dispatcher.Conf.Builder()
                .topic(topicFor(key))
                .type(key.getTypeLiteral().getType())
                .group(group)
                .build());
      }
    };
  }

  @Provides
  public @Shared Streams.DispatcherFactory sharedDispatcherFactory(Streams.DispatcherFactory dispatcherFactory) {
    return dispatcherFactory;
  }
}
