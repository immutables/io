package io.immutables.micro.tester;


import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Messages;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Types;
import io.immutables.micro.DatabaseScript;
import io.immutables.micro.Databases;
import io.immutables.micro.Manifest;
import io.immutables.micro.MixinModule;
import io.immutables.micro.References;
import io.immutables.micro.Servicelet;
import io.immutables.micro.wiring.ServiceletNameModule;
import io.immutables.regres.ConnectionProvider;
import io.immutables.regres.SqlAccessor;
import io.immutables.stream.Receiver;
import org.mockito.Mockito;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import static io.immutables.micro.tester.SetupModule.SETUPS;

/**
 * Tester Servicelet facets composer which builds the servicelet which "tests"
 * servicelets under test. Practically, the tester servicelet is then used to inject test object
 * instance.
 */
final class Facets extends io.immutables.micro.Facets {
  TesterFacets.Broker broker = TesterFacets.Broker.IN_MEMORY;

  // This is rare example of code for extending non-abstract class in ad-hoc manner
  // having A bunch or protected access and having inherited base facets plus tester
  // specific facets bolted on top
  // (not proud of this design but not shy of it, fits testing purposes)

  Facets(String id) {
    super(id);
    skipSourcesPrefixed(Facets.class.getName());
    skipSourcesPrefixed(TesterFacets.class.getName());

    platformBlocks.add(this::mixinAccessRepository);
    platformBlocks.add(binder -> Multibinder.newSetBinder(binder, TesterFacets.When.class));
  }

  final Set<Servicelet> underTest = new LinkedHashSet<>();
  final TesterFacets facets = new TesterFacets() {
    @Override
    public TesterFacets http(Consumer<TesterHttpFacet> configure) {
      configureNoOverlap(configure, http);
      return this;
    }

    @Override
    public TesterFacets database(Consumer<TesterDatabaseFacet> configure) {
      configureNoOverlap(configure, database);
      return this;
    }

    @Override
    public TesterFacets stream(Consumer<TesterStreamFacet> configure) {
      configureNoOverlap(configure, stream);
      return this;
    }

    @Override
    public TesterFacets configure(Consumer<Binder> configure) {
      Facets.super.configure(configure);
      return this;
    }

    @Override
    public TesterFacets setup(String section, Object setup) {
      StackTraceElement source = getCallingSource(this);
      Facets.super.configurePlatform(b ->
          MapBinder.newMapBinder(b.withSource(source), String.class, Object.class, SETUPS)
              .addBinding(section)
              .toInstance(setup)
      );
      return this;
    }

    @Override
    public TesterFacets servicelets(Servicelet... underTest) {
      Facets.this.underTest.addAll(Arrays.asList(underTest));
      return this;
    }
  };

  private final TesterFacets.TesterHttpFacet http = new TesterFacets.TesterHttpFacet() {
    @Override
    public <I> RequireEndpoint<I> require(Key<I> key) {
      return Facets.super.http.require(key);
    }

    @Override
    public <I> ProvideEndpoint<I> provide(Key<I> key) {
      HttpFacet.ProvideEndpoint<I> httpProvide = Facets.super.http.provide(key);
      return new ProvideEndpoint<>() {
        @Override
        public void bind(Consumer<LinkedBindingBuilder<I>> binding) {
          httpProvide.bind(binding);
        }

        @Override
        public void unbound() {
          httpProvide.unbound();
        }

        @Override
        public void bindMock() {
          I mock = createMock(key);
          bindInstance(mock);
          StackTraceElement source = getCallingSource(this);
          platformBlocks.add(b -> {
            resetMockAfterEach(b.withSource(source), mock);
          });
        }
      };
    }

    @Override
    public void requireAll() {
      HttpFacet http = Facets.super.http;

      if (underTest.isEmpty()) throw new AssertionError(
          "Please specify servicelets under test for using http.requireAll");

      for (Servicelet s : underTest) {
        for (Manifest.Resource r : s.manifest().resources()) {
          if (r.kind() == Manifest.Kind.HTTP_PROVIDE) {
            http.require(References.key(r.reference()));
          }
        }
      }
    }

    @Override
    public String toString() {
      return Facets.super.http.toString();
    }
  };

  private final TesterFacets.TesterDatabaseFacet database = new TesterFacets.TesterDatabaseFacet() {
    @Override
    public ForServicelet using(Servicelet target) {

      Servicelet.Name targetName = target.manifest().name();
      return new ForServicelet() {
        @Override
        public <R> TesterFacets.Qualified<R> repository(Class<R> repositoryInterface) {
          StackTraceElement source = getCallingSource(database);

          Key<R> key = Key.get(repositoryInterface);
          // we get this function during actual binding in servicelet block
          // so we allow DSL to mutate it in dangling DSL calls.
          // Identity transform unless changed via Qualified
          AtomicReference<Function<Key<R>, Key<R>>> keyQualifier = new AtomicReference<>(t -> t);

          serviceletBlocks.add(b -> {
            Key<R> testerQualifiedKey = keyQualifier.get().apply(key);
            b.withSource(source)
                .bind(testerQualifiedKey)
                .toProvider(Databases.repositoryFromServicelet(key, targetName));
          });
          return keyQualifier::set;
        }

        @Override
        public TesterFacets.When init(DatabaseScript script) {
          StackTraceElement source = getCallingSource(database);
          AtomicReference<RunWhen> when = new AtomicReference<>(RunWhen.BEFORE_ALL);
          platformBlocks.add(b -> {
            Multibinder.newSetBinder(b.withSource(source), TesterFacets.When.class)
                .addBinding()
                .toInstance(initForServicelet(targetName, script, when.get()));
          });
          return new TesterFacets.When() {
            @Override
            public void beforeAll() {
              when.set(RunWhen.BEFORE_ALL);
            }

            @Override
            public void afterAll() {
              when.set(RunWhen.AFTER_ALL);
            }

            @Override
            public void beforeEach() {
              when.set(RunWhen.BEFORE_EACH);
            }

            @Override
            public void afterEach() {
              when.set(RunWhen.AFTER_EACH);
            }
          };
        }
      };
    }

    @Override
    public String toString() {
      return Facets.super.database.toString();
    }
  };

  enum RunWhen {
    BEFORE_ALL, AFTER_ALL, BEFORE_EACH, AFTER_EACH
  }

  private TesterFacets.When initForServicelet(Servicelet.Name targetName, DatabaseScript script, RunWhen when) {
    return new TesterFacets.When() {
      @Inject
      ServiceletNameModule.Lookup lookup;

      @Override
      public void beforeAll() {
        if (when == RunWhen.BEFORE_ALL) execute();
      }

      @Override
      public void afterAll() {
        if (when == RunWhen.AFTER_ALL) execute();
      }

      @Override
      public void beforeEach() {
        if (when == RunWhen.BEFORE_EACH) execute();
      }

      @Override
      public void afterEach() {
        if (when == RunWhen.AFTER_EACH) execute();
      }

      private void execute() {
        BridgeRepository repository = lookup.injectorBy(targetName)
            .orElseThrow(AssertionError::new)
            .getInstance(BridgeRepository.class);
        try (ConnectionProvider.Handle handle = repository.connectionHandle()) {
          script.execute(handle.connection);
        } catch (SQLException ex) {
          throw new AssertionError(String.format("Failed database init for %s with %s", name, script), ex);
        }
      }
    };
  }

  private final TesterFacets.TesterStreamFacet stream = new TesterFacets.TesterStreamFacet() {

    @Override
    public void broker(TesterFacets.Broker broker) {
      Facets.this.broker = broker;
    }

    @Override
    public <R> ProduceRecords<R> produce(Key<R> key) {
      return Facets.super.stream.produce(key);
    }

    @Override
    public <R> ConsumeRecords<R> consume(Key<R> key) {
      StreamsFacet.ConsumeRecords<R> consume = Facets.super.stream.consume(key);
      return new ConsumeRecords<>() {
        @Override
        public void unbound() {
          consume.unbound();
        }

        @Override
        public void bind(Consumer<LinkedBindingBuilder<Receiver<R>>> binding) {
          consume.bind(binding);
        }

        @Override
        public GroupConsumeRecords<R> inGroup(String group) {
          consume.inGroup(group);
          return this;
        }

        @Override
        public TesterFacets.Qualified<RecordBuffer<R>> bindBuffer() {
          BlockingRecordBuffer<R> buffer = new BlockingRecordBuffer<>(10, TimeUnit.SECONDS);

          bind(b -> b.toInstance(buffer));

          StackTraceElement source = getCallingSource(stream);
          @SuppressWarnings("unchecked") // safe unchecked verified by reflective composition
          Key<RecordBuffer<R>> bufferKey = (Key<RecordBuffer<R>>) Key.get(
              Types.newParameterizedType(RecordBuffer.class, key.getTypeLiteral().getType()));
          // we get this function during actual binding in servicelet block
          // so we allow DSL to mutate it in dangling DSL calls.
          // Identity transform unless changed via Qualified
          Function<Key<RecordBuffer<R>>, Key<RecordBuffer<R>>> defaultQualifier =
              key.hasAttributes()
                  ? t -> Key.get(bufferKey.getTypeLiteral(), key.getAnnotation())
                  : key.getAnnotation() != null
                  ? t -> Key.get(bufferKey.getTypeLiteral(), key.getAnnotationType())
                  : t -> t;

          var keyQualifier = new AtomicReference<>(defaultQualifier);

          serviceletBlocks.add(b -> {
            Key<RecordBuffer<R>> testerQualifiedKey = keyQualifier.get().apply(bufferKey);
            b.withSource(source)
                .bind(testerQualifiedKey)
                .toInstance(buffer);
          });

          platformBlocks.add(b -> {
            Multibinder.newSetBinder(b.withSource(source), TesterFacets.When.class)
                .addBinding()
                .toInstance(new TesterFacets.When() {
                  @Override
                  public void afterEach() {
                    buffer.reset();
                  }
                });
          });
          return keyQualifier::set;
        }
      };
    }

    @Override
    public String toString() {
      return Facets.super.stream.toString();
    }
  };

  private void mixinAccessRepository(Binder platformBinder) {
    Multibinder.newSetBinder(platformBinder, MixinModule.class)
        .addBinding()
        .toInstance(b -> {
          b.bind(BridgeRepository.class).toProvider(
              Databases.repositoryOwnProvider(Key.get(BridgeRepository.class)));
        });
  }

  // safe unchecked runtime raw type should match (for mocking purposes)
  @SuppressWarnings("unchecked")
  private static <I> I createMock(Key<I> key) {
    Class<? super I> type = key.getTypeLiteral().getRawType();
    // try Guice's key formatting as a name for mock, lets see how it fares
    String name = Messages.format("%s", key);
    return (I) Mockito.mock(type, name);
  }

  private void resetMockAfterEach(Binder binder, Object mock) {
    // An alternative would be contribute mock to multibinding
    // and reset all mocks, well this should be quite
    Multibinder.newSetBinder(binder, TesterFacets.When.class)
        .addBinding()
        .toInstance(new TesterFacets.When() {
          @Override
          public void afterEach() {
            Mockito.reset(mock);
          }
        });
  }

  /**
   * This repository is used internally by tester servicelet to access target servicelet's database for general
   * purposes, like running init/cleanup scripts within that database.
   */
  interface BridgeRepository extends SqlAccessor {
  }
}
