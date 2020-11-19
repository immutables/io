package io.immutables.micro.wiring;

import io.immutables.micro.Launcher;
import io.immutables.micro.MixinModule;
import io.immutables.micro.Servicelet;
import io.immutables.micro.Systems;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Singleton;
import com.google.inject.*;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.multibindings.ProvidesIntoSet;

// TODO It is unclear the need in this if we manage Manifest, which contains name too
// and tests (micro platform internal) still need to construct names and manifests in setup,
// so having name as separate module doesn't serve well. Possibly just replace with unified
// Manifest based facility and tests would create ad-hoc manifest pretty easisy, as easy as generating
// name etc.
public class ServiceletNameModule extends AbstractModule {
  private static final Key<Servicelet.Name> NAME = Key.get(Servicelet.Name.class);

  private final AtomicInteger autoIdCounter = new AtomicInteger();

  @ProvidesIntoSet
  public MixinModule defaultName() {
    return binder -> {
      OptionalBinder.newOptionalBinder(binder, NAME)
          .setDefault()
          .toProvider(this::automaticName)
          .in(Scopes.SINGLETON);
    };
  }

  private Servicelet.Name automaticName() {
    return Servicelet.name("-servicelet-" + autoIdCounter.getAndIncrement());
  }

  /**
   * Assigns explicit name to the servicelet binder, this is binder DSL equivalent of following annotation:
   *
   * <pre>
   * &#64;ProvidesIntoOptional(Type.ACTUAL)
   * public Servicelet.Name name() {
   *   return Servicelet.name("name");
   * }
   * </pre>
   */
  public static void assignName(Binder binder, Servicelet.Name name) {
    OptionalBinder.newOptionalBinder(binder, NAME)
        .setBinding()
        .toInstance(name);
  }

  public static Servicelet.Name getName(Injector module) {
    return module.getInstance(NAME);
  }

  @Provides
  @Singleton
  public @Systems ConcurrentMap<Servicelet.Name, Injector> byName() {
    return new ConcurrentHashMap<>();
  }

  public interface Lookup {
    Optional<Injector> injectorBy(Servicelet.Name name);
  }

  @Provides
  public Lookup lookup(@Systems ConcurrentMap<Servicelet.Name, Injector> serviceletMap) {
    return name -> Optional.ofNullable(serviceletMap.get(name));
  }

  /**
   * Fills in system map of servicelets by id. This may be useful for modules to find servicelet.
   */
  @ProvidesIntoSet
  public Launcher.ServiceletLifecycle indexByName(@Systems ConcurrentMap<Servicelet.Name, Injector> byName) {
    return servicelet -> byName.put(getName(servicelet), servicelet);
  }
}
