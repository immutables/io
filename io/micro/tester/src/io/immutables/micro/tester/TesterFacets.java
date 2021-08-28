package io.immutables.micro.tester;

import io.immutables.micro.Servicelet;
import io.immutables.micro.DatabaseScript;
import io.immutables.micro.ServiceletFacets;
import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Function;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.name.Names;

/**
 * This interface is equivalent to {@link ServiceletFacets} but limits and guides API/DSL
 * for configuring tester servicelet specifically. We're not extending ServiceletFacets but reuse of extends facets from it,
 * where it makes sense, avoiding clashes of generic signatures between TesterFacets and ServiceletFacets.
 */
public interface TesterFacets {
  /**
   * References servicelets under test (usually accessed as static constants).
   * These servicelets and the tester servicelet will be instantiated on this test platform.
   */
  TesterFacets servicelets(Servicelet... underTest);

  TesterFacets http(Consumer<TesterHttpFacet> configure);

  TesterFacets stream(Consumer<TesterStreamFacet> configure);

  TesterFacets database(Consumer<TesterDatabaseFacet> configure);

  TesterFacets configure(Consumer<Binder> configure);

  TesterFacets setup(String section, Object setup);

  interface TesterHttpFacet extends ServiceletFacets.HttpFacet {
    @Override
    <I> ProvideEndpoint<I> provide(Key<I> key);

    @Override
    default <I> ProvideEndpoint<I> provide(Class<I> endpointType) {
      return provide(Key.get(endpointType));
    }

    @Override
    default <I> ProvideEndpoint<I> provide(Class<I> endpointType, String named) {
      return provide(Key.get(endpointType, Names.named(named)));
    }

    void requireAll();

    interface ProvideEndpoint<I> extends ServiceletFacets.HttpFacet.ProvideEndpoint<I> {
      void bindMock();
    }
  }

  /**
   * Databases are special because they are owned by A servicelet and are not provided/consumed as
   * shared resources. However the there is semi-transparent information about what is happening
   * inside the servicelet
   */
  interface TesterDatabaseFacet {
    /**
     * We can only target here databases of other servicelets under test.
     * We don't have tester's own database, so we point it here first
     */
    ForServicelet using(Servicelet target);

    interface ForServicelet {
      <R> Qualified<R> repository(Class<R> repositoryInterface);

      When init(DatabaseScript script);

      default When sql(String statements) {
        return init(DatabaseScript.usingSql(statements));
      }

      default When sqlResource(String resource) {
        return init(DatabaseScript.fromResource(getClass(), resource));
      }
    }
  }

  /**
   * Purpose of this is to qualify if necessary and disambiguate the instance type as it is
   * injected inside tester servicelets. It is useful when there could be repositories or message
   * listeners (by the same type) available in servicelets under test, but tester servicelet want
   * to inject these instances and have to disambiguate them inside tester (or otherwise duplicate
   * binding will be created).
   */
  interface Qualified<T> {
    /**
     * Qualify binding inside tester servicelet by changing key in type-compatible manner, usually
     * by adding (or changing) qualifier annotation.
     */
    void qualify(Function<Key<T>, Key<T>> testerQualifier);

    default void annotate(Class<? extends Annotation> testerQualifier) {
      qualify(t -> Key.get(t.getTypeLiteral(), testerQualifier));
    }

    default void annotate(Annotation testerQualifier) {
      qualify(t -> Key.get(t.getTypeLiteral(), testerQualifier));
    }

    default void name(String name) {
      qualify(t -> Key.get(t.getTypeLiteral(), Names.named(name)));
    }
  }

  interface TesterStreamFacet extends ServiceletFacets.StreamsFacet {

    void broker(Broker type);

    @Override
    <R> ConsumeRecords<R> consume(Key<R> key);

    @Override
    default <R> ConsumeRecords<R> consume(Class<R> recordType) {
      return consume(Key.get(recordType));
    }

    @Override
    default <R> ProduceRecords<R> produce(Class<R> recordType, String named) {
      return produce(Key.get(recordType, Names.named(named)));
    }

    @Override
    default <R> ConsumeRecords<R> consume(Class<R> recordType, String named) {
      return consume(Key.get(recordType, Names.named(named)));
    }

    interface ConsumeRecords<R> extends ServiceletFacets.StreamsFacet.ConsumeRecords<R>, GroupConsumeRecords<R> {
      @Override
      GroupConsumeRecords<R> inGroup(String group);
    }

    interface GroupConsumeRecords<R> extends ServiceletFacets.StreamsFacet.GroupConsumeRecords<R> {

      Qualified<RecordBuffer<R>> bindBuffer();
    }
  }

  /** When to call a hook, some operation: before or after tests or suite. */
  interface When {
    default void beforeAll() {}
    default void afterAll() {}
    default void beforeEach() {}
    default void afterEach() {}
  }

  enum Broker {
    KAFKA, IN_MEMORY, PROXY
  }
}
