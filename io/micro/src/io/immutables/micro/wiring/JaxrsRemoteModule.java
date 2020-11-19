package io.immutables.micro.wiring;

import io.immutables.codec.OkJson;
import io.immutables.micro.*;
import io.immutables.micro.wiring.jersey.WebResourceFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Qualifier;
import javax.ws.rs.client.Client;
import javax.ws.rs.ext.ParamConverterProvider;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class JaxrsRemoteModule implements Module {
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  public @interface InPlatform {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  public @interface Resolved {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  public @interface Discovered {}

  @Override
  public void configure(Binder binder) {
    Multibinder.newSetBinder(binder, Jaxrs.EndpointResolver.class);
  }

  @Provides
  public @Systems.Shared
  Jaxrs.EndpointProxyProvider sharedEndpointProxyProvider(Jaxrs.EndpointProxyProvider proxyProvider) {
    return proxyProvider;
  }

  @Provides
  public @Systems.Shared Jaxrs.WebTargeter sharedWebTargeter(Jaxrs.WebTargeter targeter) {
    return targeter;
  }

  @Provides
  @Singleton
  public Jaxrs.EndpointProxyCreator endpointProxyCreator(Client client, ParamConverterProvider converter) {
    return endpoint -> WebResourceFactory.newResource(
        endpoint.key().getTypeLiteral().getRawType(),
        client.target(endpoint.target()),
        converter);
  }

  @Provides
  @Singleton
  public Jaxrs.WebTargeter webTargeter(Client client, EndpointCatalog catalog) {
    return key -> client.target(catalog.get(key).target());
  }

  // Holder needed to compare endpoint and not recreate delegate proxy
  // if it's haven't changed, alternatives from getting this from delegating proxy handler
  // seems more cumbersome
  private static final class ProxyHandler extends DelegatingProxyHandler {
    private final Jaxrs.EndpointProxyCreator proxyCreator;
    Jaxrs.EndpointEntry endpoint;
    Object delegate;

    ProxyHandler(Class<?> interfaceType, Jaxrs.EndpointEntry endpoint, Jaxrs.EndpointProxyCreator proxyCreator) {
      super(interfaceType);
      this.proxyCreator = proxyCreator;
      updateEndpoint(endpoint);
    }

    @Override
    Object delegate() {
      return delegate;
    }

    void updateEndpoint(Jaxrs.EndpointEntry endpoint) {
      this.endpoint = endpoint;
      this.delegate = proxyCreator.create(endpoint);
    }

    static ProxyHandler getHandler(Object object) {
      return (ProxyHandler) Proxy.getInvocationHandler(object);
    }
  }

  @Provides
  @Singleton
  public @Systems LoadingCache<Key<?>, Object> proxyCache(
      Client client,
      Jaxrs.Setup setup,
      EndpointCatalog catalog,
      Jaxrs.EndpointProxyCreator proxyCreator) {

    return CacheBuilder.newBuilder()
        .refreshAfterWrite(setup.refreshEndpointsAfter())
        .build(new CacheLoader<>() {
          @Override
          public Object load(Key<?> key) {
            return newProxy(key, catalog.get(key));
          }

          @Override
          public ListenableFuture<Object> reload(Key<?> key, Object oldValue) {
            Jaxrs.EndpointEntry newEndpoint = catalog.get(key);
            ProxyHandler proxy = ProxyHandler.getHandler(oldValue);
            if (!proxy.endpoint.equals(newEndpoint)) {
              proxy.updateEndpoint(newEndpoint);
            }
            return Futures.immediateFuture(oldValue);
          }

          private Object newProxy(Key<?> key, Jaxrs.EndpointEntry endpoint) {
            Class<?> type = key.getTypeLiteral().getRawType();
            assert type == endpoint.key().getTypeLiteral().getRawType();
            return new ProxyHandler(type, endpoint, proxyCreator).newProxy();
          }
        });
  }

  @Provides
  @Singleton
  public @Resolved Map<Key<?>, Jaxrs.EndpointEntry> resolved(@Systems LoadingCache<Key<?>, Object> cache) {
    return Maps.transformValues(cache.asMap(), h -> ProxyHandler.getHandler(h).endpoint);
  }

  @ProvidesIntoSet
  public Service maintainEndpoints(
      Jaxrs.Setup setup,
      @Systems LoadingCache<Key<?>, Object> cache,
      @Discovered Set<Jaxrs.EndpointEntry> endpoints,
      @Resolved Map<Key<?>, Jaxrs.EndpointEntry> resolved,
      Set<Jaxrs.EndpointResolver> dynamicEndpointSuppliers) {
    return new AbstractScheduledService() {
      final AtomicLong iterations = new AtomicLong();

      @Override
      protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(20, 200, TimeUnit.MILLISECONDS);
      }

      @Override
      protected void runOneIteration() throws Exception {
        // forcing any refreshes, cleanUp doesn't help here
        for (Key<?> k : cache.asMap().keySet()) {
          cache.getIfPresent(k);
        }
        // this functionality is merged here to avoid creating too many
        // services, for external observer we still just maintaining endpoints here
        // whether resolved or discovered.
        // We're do this every 8-th iteration to avoid creating separate schedule
        if (setup.discover() && iterations.getAndIncrement() % 8 == 0) {
          maintainDiscoveredEndpoints();
        }
      }

      private void maintainDiscoveredEndpoints() {
        Collection<Jaxrs.EndpointEntry> entries = new ArrayList<>(setup.endpoints());
        entries.addAll(resolved.values());
        for (var supplier : dynamicEndpointSuppliers) {
          entries.addAll(supplier.scan());
        }
        // we can switch to forwarding collection with atomically replace internal
        // delegate, but for now, we just mutably adjust concurrent set.
        endpoints.retainAll(entries); // at first we remove old, not present here entries
        endpoints.addAll(entries); // then add newly discovered if any
      }

      @Override
      protected String serviceName() {
        return "EndpointMaintainer";
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Provides
  @Singleton
  public Jaxrs.EndpointProxyProvider endpointProvider(@Systems LoadingCache<Key<?>, Object> cache) {
    return new Jaxrs.EndpointProxyProvider() {
      @Override
      public <T> T get(Key<T> key) {
        cache.refresh(key);
        return (T) cache.getUnchecked(key);
      }
    };
  }

  @Provides
  @Singleton
  public @InPlatform Map<Key<?>, Jaxrs.EndpointEntry> inPlatformEntries(
      Jaxrs.Setup setup,
      @Jaxrs.Registered HostAndPort hostPort,
      Map<Servicelet.Name, Manifest> manifests) {

    Origin origin = new Origin.Builder()
        .descends(setup.origin())
        .resourceFromStackTrace()
        .build();

    List<Jaxrs.EndpointEntry> entries = new ArrayList<>();

    for (Manifest m : manifests.values()) {
      for (Manifest.Resource r : m.resources()) {
        if (r.kind() == Manifest.Kind.HTTP_PROVIDE) {
          URI target = toUri(m.name(), hostPort);
          entries.add(Jaxrs.EndpointEntry.of(r.reference(), target)
              .withOrigin(origin));
        }
      }
    }

    return Maps.uniqueIndex(entries, Jaxrs.EndpointEntry::key);
  }

  @Provides
  @Singleton
  public @Discovered Set<Jaxrs.EndpointEntry> discoveredEndpoints(Jaxrs.Setup setup) {
    return setup.discover()
        ? new CopyOnWriteArraySet<>()
        : Set.of();
  }

  @Provides
  @Singleton
  public EndpointCatalog catalog(
      Jaxrs.Setup setup,
      @Jaxrs.Registered HostAndPort hostPort,
      @InPlatform Map<Key<?>, Jaxrs.EndpointEntry> inPlatform,
      Set<Jaxrs.EndpointResolver> dynamicEndpointSuppliers) {
    return new EndpointCatalog(
        setup,
        inPlatform,
        hostPort,
        dynamicEndpointSuppliers);
  }

  @Provides
  @Singleton
  public DirectoryEndpoints directoryEndpoints(
      Jaxrs.Setup setup,
      OkJson json,
      ExceptionSink exceptions,
      @InPlatform Map<Key<?>, Jaxrs.EndpointEntry> inPlatform) {
    return new DirectoryEndpoints(setup, json, exceptions, inPlatform.values());
  }

  // for startUp/shutDown and runOneIteration lifecycle scheduling
  @ProvidesIntoSet
  public Service catalogAsService(DirectoryEndpoints service) {
    return service;
  }

  // for startUp/shutDown and runOneIteration lifecycle scheduling
  @ProvidesIntoSet
  public Jaxrs.EndpointResolver asSupplier(DirectoryEndpoints service) {
    return service;
  }

  private static URI toUri(Servicelet.Name name, HostAndPort hostPort) {
    return URI.create(String.format("http://%s/%s", hostPort, name));
  }
}
