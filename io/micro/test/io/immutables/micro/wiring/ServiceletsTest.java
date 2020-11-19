package io.immutables.micro.wiring;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.Stage;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import io.immutables.micro.Keys;
import io.immutables.micro.Launcher;
import io.immutables.micro.Systems;
import static io.immutables.that.Assert.that;

public class ServiceletsTest {
  @Test
  public void emptyServiceletSetAndStage() {
    Injector injector = new Launcher(Stage.PRODUCTION).inject();
    // "development" stage is the default in Guice, so we check "production"
    that(injector.getInstance(Stage.class)).equalTo(Stage.PRODUCTION);
    that(injector.getInstance(SERVICELETS)).isEmpty();
  }

  @Test
  public void serviceletsMixins() {
    Injector injector = new Launcher()
        .add(Launcher.mixin(b -> {
          b.bind(SampleObject.class).in(Scopes.SINGLETON);
        }))
        .addServicelet(b -> {})
        .addServicelet(b -> {})
        .inject();

    List<Injector> servicelets = new ArrayList<>(injector.getInstance(SERVICELETS));
    // check that we have 2 servicelets
    that(servicelets).hasSize(2);
    // each having singleton Sample via mixin
    that(servicelets.get(0).getInstance(SampleObject.class))
        .same(servicelets.get(0).getInstance(SampleObject.class));
    // each having separare singleton instance
    that(servicelets.get(0).getInstance(SampleObject.class))
        .notSame(servicelets.get(1).getInstance(SampleObject.class));
  }

  @Test
  public void sharedObjectsForServicelets() {
    Injector injector = new Launcher()
        .add(b -> {
          b.bind(Key.get(SampleObject.class, Systems.Shared.class))
              .to(SampleObject.class)
              .in(Scopes.SINGLETON);
        })
        .addServicelet(b -> {})
        .addServicelet(b -> {})
        .inject();

    List<Injector> servicelets = new ArrayList<>(injector.getInstance(SERVICELETS));
    // check that we have 2 servicelets
    that(servicelets).hasSize(2);
    // each having same shared singleton exposed by platform
    that(servicelets.get(0).getInstance(SampleObject.class))
        .same(servicelets.get(1).getInstance(SampleObject.class));
  }

  @Test
  public void serviceManagerForServices() {
    Injector injector = new Launcher()
        .add(new ServiceManagerModule())
        .add(b -> addService(b).toProvider(SampleService::new))
        .addServicelet(b -> {
          addService(b).toProvider(SampleService::new);
          addService(b).toProvider(SampleService::new);
        })
        .addServicelet(b -> {
          addService(b).toProvider(SampleService::new);
        })
        .inject();

    ServiceManager manager = injector.getInstance(ServiceManager.class);

    // 1 in platform, 2 in one servicelet, 1 in another
    that(manager.servicesByState().values()).hasSize(4);

    that(manager.servicesByState().keySet()).isOf(Service.State.NEW);
    manager.startAsync().awaitHealthy();
    that(manager.servicesByState().keySet()).isOf(Service.State.RUNNING);
    manager.stopAsync().awaitStopped();
    that(manager.servicesByState().keySet()).isOf(Service.State.TERMINATED);
  }

  private static LinkedBindingBuilder<Service> addService(Binder b) {
    return Multibinder.newSetBinder(b, Service.class).addBinding();
  }

  static class SampleObject {}

  static class SampleService extends AbstractIdleService {
    @Override
    protected void startUp() {}
    @Override
    protected void shutDown() {}
  }

  private static final Key<Set<Injector>> SERVICELETS = Keys.setOf(Injector.class, Systems.class);
}
