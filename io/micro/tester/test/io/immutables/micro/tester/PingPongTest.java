package io.immutables.micro.tester;

import io.immutables.micro.Servicelet;
import io.immutables.micro.Facets;
import io.immutables.stream.Sender;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;
import static io.immutables.that.Assert.that;

@RunWith(ServiceletTester.class)
public class PingPongTest {
  static final Servicelet Ponglet = new Facets("ponglet")
      .stream(s -> {
        var pong = s.produce(String.class, "pong").sender();

        s.consume(String.class, "ping")
            .inGroup("PingPong")
            .bindInstance(records -> {
              for (String r : records) {
                pong.write(r + "<<<");
              }
            });
      })
      .toServicelet();

  public static void init(TesterFacets t) {
    t.servicelets(Ponglet)
        .stream(s -> {
          s.produce(String.class, "ping");
          s.consume(String.class, "pong").bindBuffer();
          s.broker(TesterFacets.Broker.IN_MEMORY);
        });
  }

  @Inject @Named("ping") Sender<String> ping;
  @Inject @Named("pong") RecordBuffer<String> pong;

  @Test public void pingPong() {
    ping.write("1");
    ping.write("2");
    ping.write("3");

    that(pong
        .giveupAfter(5, TimeUnit.SECONDS)
        .take(3))
        .hasOnly("1<<<", "2<<<", "3<<<");
  }

  @Test public void pongFilter() {
    ping.write("1");
    ping.write("2");
    ping.write("3");

    that(pong
        .giveupAfter(5, TimeUnit.SECONDS)
        .filter(p -> p.contains("2<<<"))
        .take(1)).hasSize(1);
  }
}
