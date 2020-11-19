package io.immutables.micro.wiring;

import com.google.inject.Key;
import com.google.inject.name.Names;
import io.immutables.micro.Facets;
import io.immutables.micro.Servicelet;

// not a unit test by intention
public class MainSample2 {
  private static final Servicelet SERVICELET2 = new Facets("another-servicelet")
      .http(h -> {
        h.provide(Key.get(Res.class, Names.named("a"))).bindInstance(() -> "Amiga!");
      })
      .toServicelet();

  public static void main(String... args) {

    System.out.println(SERVICELET2.manifest());
    new MainLauncher(args).use(SERVICELET2).run();
  }
}
