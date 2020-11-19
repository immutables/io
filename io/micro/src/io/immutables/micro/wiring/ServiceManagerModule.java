package io.immutables.micro.wiring;

import io.immutables.micro.Keys;
import io.immutables.micro.MixinModule;
import io.immutables.micro.Systems;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Singleton;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ServiceManager.Listener;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

/**
 * Collects services from both platform and servicelets to create instance of {@link ServiceManager}. Does not impose
 * any hierarchy between services, so all services startup and shutdown independently and asynchronously [depending on
 * their executor].
 */
public final class ServiceManagerModule implements Module {

  @Override
  public void configure(Binder binder) {
    managedServices(binder);
    managedListeners(binder);
  }

  /**
   * This mixin ensures that multibinding is declared in each of the servicelets so that we can get empty set from it if
   * no services are actually defined by servicelet (and not an error about absent binding)
   */
  @ProvidesIntoSet
  public MixinModule managedServicesInServicelet() {
    return this::configure;
  }

  @Provides
  @Singleton
  public ServiceManager manager(
      Injector platform,
      @Systems Provider<Set<Injector>> servicelets) {
    List<Service> allServices = new ArrayList<>(platform.getInstance(SERVICES));
    for (Injector m : servicelets.get()) {
      allServices.addAll(m.getInstance(SERVICES));
    }
    allServices.removeIf(s -> s == NOOP);

    ServiceManager manager = new ServiceManager(allServices);

    List<ServiceManager.Listener> allListeners = new ArrayList<>(platform.getInstance(LISTENERS));
    for (Injector m : servicelets.get()) {
      allListeners.addAll(m.getInstance(LISTENERS));
    }

    allListeners.forEach(l -> manager.addListener(l, MoreExecutors.directExecutor()));
    return manager;
  }

  public static Multibinder<Service> managedServices(Binder binder) {
    return Multibinder.newSetBinder(binder, Service.class);
  }

  public static Multibinder<Listener> managedListeners(Binder binder) {
    return Multibinder.newSetBinder(binder, Listener.class);
  }

  public static Service noop() {
    return NOOP;
  }

  private static final Key<Set<Service>> SERVICES = Keys.setOf(Service.class);
  private static final Key<Set<Listener>> LISTENERS = Keys.setOf(Listener.class);

  private static final Service NOOP = new AbstractIdleService() {
    @Override protected void startUp() {}

    @Override protected void shutDown() {}
  };
}
