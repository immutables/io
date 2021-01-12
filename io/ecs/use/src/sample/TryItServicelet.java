package sample;

import io.immutables.micro.Servicelet;
import io.immutables.micro.wiring.MainLauncher;
import java.util.List;
import static io.immutables.micro.Facets.healthy;
import static io.immutables.micro.Facets.servicelet;

public interface TryItServicelet {
  Servicelet tryit = servicelet("tryit", (http, stream, database, configure) -> {
    stream.produce(String.class).unbound();
    stream.consume(Integer.class).unbound();
    stream.consume(Void.class).unbound();

    http.provide(End.class).bindInstance(new End() {
      @Override
      public String get() {
        return "HO!";
      }

      @Override
      public List<Inl> get(Inl in) {
        return List.of(in, Inl.of("X"), Inl.of("Y"), Inl.of("Z"));
      }
    });

		var ping = stream.produce(String.class, "ping").sender();
    var pong = stream.produce(String.class, "pong").sender();

    stream.consume(String.class, "ping")
        .inGroup("y")
        .bindInstance(records -> {
          records.forEach(r -> {
            pong.write(r + " << BACK ");
          });
        });

    stream.consume(String.class, "pong")
        .inGroup("y")
        .bindInstance(records -> {
          records.forEach(r -> {
            System.err.println("PONG::::: " + r);
          });
        });

    configure.accept(healthy(() -> {
      System.err.println("!!!!!");

      ping.write("1");
      ping.write("2");
      ping.write("3");
    })::accept);
  });

  static void main(String[] args) {
    new MainLauncher(args).use(tryit).run();
  }
}
