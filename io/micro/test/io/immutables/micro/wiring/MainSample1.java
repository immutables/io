package io.immutables.micro.wiring;

import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import javax.inject.Inject;
import javax.inject.Provider;
import io.immutables.micro.Facets;
import io.immutables.micro.Servicelet;

// not a unit test by intention
public class MainSample1 {

  private static final Servicelet SERVICELET = new Facets("sample-servicelet")
      .http(h -> {
        h.provide(Res.class).bind(b -> {
          b.toProvider(new Provider<Res>() {
            @Inject
            @Named("a")
            Res res;
            @Override
            public Res get() {
              return () -> "Hello " + res.get();
            }
          });
        });
        h.require(Key.get(Res.class, Names.named("a")));
      })
      .database(d -> {})
      .toServicelet();

  public static void main(String... args) {
    new MainLauncher(args).use(SERVICELET).run();
  }
}
