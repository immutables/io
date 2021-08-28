package io.immutables.micro;

import io.immutables.stream.Receiver;
import io.immutables.stream.Sender;
import java.util.function.Consumer;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.name.Names;

/**
 * Facet configuration methods and the DSL together
 */
public interface ServiceletFacets {

  Facets http(Consumer<HttpFacet> configure);

  Facets stream(Consumer<StreamsFacet> configure);

  Facets database(Consumer<DatabaseFacet> configure);

  Facets configure(Consumer<Binder> configure);

  Facets configurePlatform(Consumer<Binder> configure);

  Facets start(Runnable healthy);

  interface HttpFacet {
    <I> RequireEndpoint<I> require(Key<I> key);

    <I> ProvideEndpoint<I> provide(Key<I> key);

    default <I> RequireEndpoint<I> require(Class<I> endpointType) {
      return require(Key.get(endpointType));
    }

    default <I> ProvideEndpoint<I> provide(Class<I> endpointType) {
      return provide(Key.get(endpointType));
    }

    default <I> RequireEndpoint<I> require(Class<I> endpointType, String named) {
      return require(Key.get(endpointType, Names.named(named)));
    }

    default <I> ProvideEndpoint<I> provide(Class<I> endpointType, String named) {
      return provide(Key.get(endpointType, Names.named(named)));
    }

    interface RequireEndpoint<I> {
      /**
       * Don't bind client proxy, only declare logical dependency.
       */
      void unbound();

      I get();
    }

    interface ProvideEndpoint<I> {
      // it would be cool to just return LinkedBindingBuilder in a fluent manner
      // and not resort to a closure/lambda, but it's quite non trivial due to the flow how we and
      // how Guice calls this (this can be mitigated with proxies and intermediary / recording
      // implementations for Binder/Binding (including using BindingBuilder etc), it's just not
      // worth it for now

      /**
       * Records binding closure receiving binding builder targeting the key of provided HTTP endpoint interface.
       * @param binding consumer for binding builder
       */
      void bind(Consumer<LinkedBindingBuilder<I>> binding);

      /**
       * Don't register resource, only declare it logically.
       */
      void unbound();

      /**
       * Conveniently binds to implementation class. This is equivalent (shortcut) of {@code
       * bind(key).to(implementationClass)}, but also registering this as JAXRS implementation of provided.
       * @param implementationClass nuff said
       */
      default void bindClass(Class<? extends I> implementationClass) {
        bind(b -> b.to(implementationClass));
      }

      /**
       * Conveniently binds to instance.
       * @param instance instance implemeting endpoint interface
       */
      default void bindInstance(I instance) {
        bind(b -> b.toInstance(instance));
      }

      default void bindKey(Key<? extends I> targetKey) {
        bind(b -> b.to(targetKey));
      }

      default void bindKey(Class<? extends I> targetKey) {
        bind(b -> b.to(Key.get(targetKey)));
      }
    }
  }

  interface StreamsFacet {
    <R> ProduceRecords<R> produce(Key<R> key);

    <R> ConsumeRecords<R> consume(Key<R> key);

    default <R> ProduceRecords<R> produce(Class<R> recordType) {
      return produce(Key.get(recordType));
    }

    default <R> ConsumeRecords<R> consume(Class<R> recordType) {
      return consume(Key.get(recordType));
    }

    default <R> ProduceRecords<R> produce(Class<R> recordType, String named) {
      return produce(Key.get(recordType, Names.named(named)));
    }

    default <R> ConsumeRecords<R> consume(Class<R> recordType, String named) {
      return consume(Key.get(recordType, Names.named(named)));
    }

    interface ProduceRecords<R> {
      /**
       * Don't bind producer client, only declare logical dependency.
       */
      void unbound();

      /** Sender which resolved with delay can be used for lambdas within servicelet definition instead of injection. */
      Sender<R> sender();
    }

    interface ConsumeRecords<R> extends GroupConsumeRecords<R> {
      GroupConsumeRecords<R> inGroup(String group);

      /**
       * Don't bind consumer client, only declare logical dependency.
       */
      void unbound();
    }

    interface GroupConsumeRecords<R> {
      /**
       * Records binding closure receiving binding builder targeting the key of record receiver
       * @param binding consumer for binding builder
       */
      void bind(Consumer<LinkedBindingBuilder<Receiver<R>>> binding);

      /**
       * Conveniently binds to implementation class. This is equivalent (shortcut) of {@code
       * bind(key).to(implementationClass)}. Will be a prototype instance instantiated for each shard assignment. Use
       * {@link #bind(Consumer)} to bind in singleton scope or to provider/delegate key.
       * @param implementationClass implementation
       */
      default void bindClass(Class<? extends Receiver<R>> implementationClass) {
        bind(b -> b.to(implementationClass));
      }

      /**
       * Conveniently binds to instance. Will be a singleton by nature.
       * @param instance instance implementing endpoint interface
       */
      default void bindInstance(Receiver<R> instance) {
        bind(b -> b.toInstance(instance));
      }

      default void bindKey(Key<? extends Receiver<R>> targetKey) {
        bind(b -> b.to(targetKey));
      }

      default void bindKey(Class<? extends Receiver<R>> targetKey) {
        bind(b -> b.to(Key.get(targetKey)));
      }
    }
  }

  /**
   * Database facet. Initially this is for SQL database and the types used for require is The kinds and instances may be
   * determined by types and qualifier. And this is currently least though out facet.
   */
  interface DatabaseFacet {
    <D> void repository(Class<D> repositoryInterface);

    <D> void record(Class<D> recordType);

    void init(DatabaseScript script);
  }
}
