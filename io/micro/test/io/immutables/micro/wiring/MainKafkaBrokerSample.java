package io.immutables.micro.wiring;

import io.immutables.micro.Facets;
import io.immutables.micro.Servicelet;
import io.immutables.micro.Streams;
import io.immutables.micro.stream.http.BrokerApi;
import io.immutables.micro.stream.http.kafka.KafkaBroker;
import io.immutables.stream.Sender;

import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import com.google.inject.Injector;
import com.google.inject.Key;

// not a unit test by intention
public class MainKafkaBrokerSample {
  private static final Servicelet SERVICELET_1 = new Facets("kafka-broker")
      .http(h -> {
        h.provide(BrokerApi.class).bindClass(KafkaBroker.class);
      })
      .toServicelet();

  private static final Servicelet SERVICELET_2 = new Facets("kafka-client")
      .stream(s -> {
        s.consume(String.class)
            .inGroup("myGroup")
            .bindInstance(records -> {
              System.out.println("Hey! I got records " + records.toString());
              System.out.println(records.stream().collect(Collectors.joining(",")));
            });
      })
      .toServicelet();

  private static final Servicelet SERVICELET_4 = new Facets("kafka-4")
      .stream(s -> {
        s.produce(String.class);
      })
      .http(h -> {
        h.provide(Res.class).bind(b -> {
          b.toProvider(new Provider<Res>() {
            @Inject
            Injector i;

            @Override
            public Res get() {
              Sender<String> sender = i.getProvider(Streams.toSenderKey(Key.get(String.class))).get();
              return () -> {
                sender.write("some message");
                return "Hello!!! I've sent message to Kafka.";
              };
            }
          });
        });
      })
      .toServicelet();

  public static void main(String... args) {
    new MainLauncher(args)
        .use(SERVICELET_1, SERVICELET_2, SERVICELET_4)
        .run();
  }
}
