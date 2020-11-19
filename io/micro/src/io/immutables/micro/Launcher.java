package io.immutables.micro;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Provider;
import com.google.common.collect.FluentIterable;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

/**
 * Launcher provides skeletal framework for launching one or more servicelets on a platform (actually zero or more, but
 * a single servicelet would be typical for production containers).
 */
public final class Launcher {
  private final Stage stage;
  private final List<Module> modules = new ArrayList<>();

  public Launcher(Stage stage) {
    this.stage = requireNonNull(stage);
  }

  public Launcher() {
    this(Stage.DEVELOPMENT);
  }

  public Launcher add(Module module) {
    modules.add(module);
    return this;
  }

  public Launcher addServicelet(Module module) {
    return add(servicelet(b -> b.install(module)));
  }

  /**
   * Turns servicelet module into platform module which contributes said servicelet to the set of servicelet.
   */
  public static Module servicelet(ServiceletModule module) {
    return binder -> serviceletModules(binder).addBinding().toInstance(module);
  }

  /**
   * Turns mixinin module into platform module which contributes said mixin to the set of mixins in a platform.
   */
  public static Module mixin(MixinModule module) {
    return binder -> mixinModules(binder).addBinding().toInstance(module);
  }

  public Injector inject() {
    AtomicReference<Set<Injector>> serviceletInjectorsRef = new AtomicReference<>();
    Injector platformInjector = createPlatformInjector(serviceletInjectorsRef::get);
    Injector sharedInjector = sharedPlatformBindings(platformInjector);
    serviceletInjectorsRef.set(createServiceletInjectors(platformInjector, sharedInjector));
    serviceletCreated(platformInjector, serviceletInjectorsRef.get());
    return platformInjector;
  }

  private void serviceletCreated(Injector platform, Set<Injector> servicelets) {
    Set<ServiceletLifecycle> platformLifecycles = platform.getInstance(SERVICELET_LIFECYCLE);
    for (Injector s : servicelets) {
      for (ServiceletLifecycle lifecycle : platformLifecycles) {
        lifecycle.created(s);
      }
    }
    for (Injector s : servicelets) {
      for (ServiceletLifecycle lifecycle : s.getInstance(SERVICELET_LIFECYCLE)) {
        lifecycle.created(s);
      }
    }
  }

  private Set<Injector> createServiceletInjectors(Injector platformInjector, Injector sharedInjector) {
    Set<MixinModule> mixins = platformInjector.getInstance(MIXINS);
    Set<ServiceletModule> servicelets = platformInjector.getInstance(SERVICELETS);

    return servicelets.stream()
        .map(module -> sharedInjector.createChildInjector(
            FluentIterable.<Module>of(this::preconfigure)
                .append(module)
                .append(mixins)
                .append(Launcher::serviceletInjected)))
        .collect(toImmutableSet());
  }

  private Injector createPlatformInjector(Supplier<Set<Injector>> injectors) {
    Module platformPreconfigure = binder -> {
      serviceletModules(binder);
      mixinModules(binder);
      binder.bind(INJECTORS).toProvider(deferredReference(INJECTORS, injectors));
    };
    return Guice.createInjector(stage, FluentIterable.<Module>of(this::preconfigure)
        .append(platformPreconfigure)
        .append(modules)
        .append(Launcher::serviceletInjected));
  }

  /**
   * Given injector it gets all of its (own) bindings qualified by {@link Systems.Shared} annotation and makes these
   * bindings available into new injector in unqualified form (unique only by type, so use these mindfully). This new
   * injector will be used as parent injector to create child injectors
   * ({@link Injector#createChildInjector(Module...)})
   * for servicelet modules.
   * @param platform platform injector
   * @return parent injector with exposed modules
   */
  @SuppressWarnings("unchecked") // relying on runtime type information in keys
  private Injector sharedPlatformBindings(Injector platform) {
    return Guice.createInjector(stage, this::preconfigure, sharedBinder -> {
      platform.getBindings().forEach((key, binding) -> {
        if (key.getAnnotationType() == Systems.Shared.class) {
          Key<Object> exposedAsKey = (Key<Object>) Key.get(key.getTypeLiteral());
          Provider<Object> toProvider = (Provider<Object>) platform.getProvider(key);
          sharedBinder.withSource(binding.getSource())
              .bind(exposedAsKey)
              .toProvider(toProvider);
        }
      });
    });
  }

  private void preconfigure(Binder binder) {
    // skipping sources from here
    binder.skipSources(Launcher.class);

    // trying to be strict, otherwise this stuff just delays problems
    binder.disableCircularProxies();
    binder.requireExactBindingAnnotations();
    binder.requireExplicitBindings();
  }

  public static Multibinder<ServiceletModule> serviceletModules(Binder binder) {
    return Multibinder.newSetBinder(binder, ServiceletModule.class);
  }

  public static Multibinder<MixinModule> mixinModules(Binder binder) {
    return Multibinder.newSetBinder(binder, MixinModule.class);
  }

  public static Multibinder<ServiceletLifecycle> serviceletInjected(Binder binder) {
    return Multibinder.newSetBinder(binder, ServiceletLifecycle.class);
  }

  private static <T> Provider<T> deferredReference(Key<T> key, Supplier<? extends T> ref) {
    return () -> {
      @Nullable T value = ref.get();
      if (value == null) throw new ProvisionException(
          "Instance by " + key + " is not available yet: do not inject it directly,"
              + " but via Provider and get instance only after injectors constructed");
      return value;
    };
  }

  public interface ServiceletLifecycle {
    void created(Injector servicelet);
  }

  private static final Key<Set<ServiceletModule>> SERVICELETS = Keys.setOf(ServiceletModule.class);
  private static final Key<Set<MixinModule>> MIXINS = Keys.setOf(MixinModule.class);
  private static final Key<Set<Injector>> INJECTORS = Keys.setOf(Injector.class, Systems.class);
  private static final Key<Set<ServiceletLifecycle>> SERVICELET_LIFECYCLE = Keys.setOf(ServiceletLifecycle.class);
}
