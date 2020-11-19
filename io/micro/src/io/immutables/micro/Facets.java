package io.immutables.micro;

import io.immutables.micro.wiring.ServiceManagerModule;
import io.immutables.micro.wiring.ServiceletNameModule;
import io.immutables.regres.SqlAccessor;
import io.immutables.stream.Receiver;
import io.immutables.stream.Sender;
import io.immutables.stream.Topic;
import io.immutables.micro.Streams.TopicImport;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;
import com.google.common.io.Resources;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Messages;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;

/**
 * Sections is a builder/configurer object for servicelets that provides access to microplatform facets configurators
 * while constructing servicelet manifest and set of microplatform modules. This is embedded DSL approach to building.
 * It is expected that Kotlin DSL can just use this facet builder/configurer DSL as underlying implementation provided
 * all the source tracing would work.
 *
 * <pre>
 * new Facets("servicelet-id")
 *   .http(http -> {
 *     // configure http facet
 *   })
 *   .database(database -> {
 *     // configure database facet
 *   });
 * })
 * .toServicelet();
 * </pre>
 * <p>
 * A platform is expected to implement a set of facets. So far the set of used facets is determined here and so far
 * there's no real extensibility (as ability to contribute new facets externally by SPI/Extension modules). On the other
 * hand, it's very hard to have such extensibility early on (and a bit naive, maybe) in light that these manifest
 * definition should be supported across microplatforms implemented, potentially, in different programming
 * languages/runtimes. Important aspect of this DSL/builder as opposing to {@link Manifest.Builder} is that it is
 * coupled with knowledge of Java/Kotlin-based microplatform implementation technologies and actually defines container
 * bindings/injection. The dependency on the capabilities/facets of the platform should be limited to the existence of
 * key component bindings/instances (and not the usage of specific modules in launcher) and some additional
 * bindings/providers would be created against interfaces and factories defined in {@link Jaxrs}, {@link Databases}
 * etc.
 * <p>
 * Microplatforms are not necessarily need to use "facet" as abstraction or an implementation component. The facets are
 * more about platform interoperability and manifest declaration/configuration. The initial Java/Kotlin implementations
 * of microplatforms will just use a set of {@link Module}s arranged according to a flavor (production, testing) of the
 * platform
 * @see Jaxrs
 * @see Databases
 */
public class Facets implements ServiceletFacets {
  protected final Supplier<Named> autoName = autoNameSupplier();
  protected final Servicelet.Name name;
  protected final List<Consumer<Binder>> platformBlocks = new ArrayList<>();
  protected final List<Consumer<Binder>> serviceletBlocks = new ArrayList<>();
  @Beta
  protected final List<Consumer<Injector>> onInjectorBlocks = new ArrayList<>();
  private final List<String> skipSourcesPrefixed = new ArrayList<>();
  // Used to prevent section overlap, semaphore is used instead of lock to also prevent reentry
  // here it's used to enforce clear section entry - exit with not overlaps
  // Note this is runtime check, but Kotlin DSL have syntactic facilities
  // to prevent this at compile time
  private final Semaphore semaphore = new Semaphore(1);

  protected final Manifest.Builder declaration = new Manifest.Builder();

  public Facets(String name) {
    this.name = Servicelet.name(name);
    declaration.name(this.name);

    skipSourcesPrefixed(
        "java.lang.reflect.",
        "sun.reflect.",
        "jdk.internal.reflect.",
        Facets.class.getName(),
        ServiceletFacets.class.getName());

    tryAttachSource();
  }

  public Facets skipSourcesPrefixed(String... prefixes) {
    skipSourcesPrefixed.addAll(Arrays.asList(prefixes));
    return this;
  }

  @Override
  public Facets http(Consumer<HttpFacet> configure) {
    configureNoOverlap(configure, http);
    return this;
  }

  @Override
  public Facets stream(Consumer<StreamsFacet> configure) {
    configureNoOverlap(configure, stream);
    return this;
  }

  @Override
  public Facets database(Consumer<DatabaseFacet> configure) {
    configureNoOverlap(configure, database);
    return this;
  }

  @Override
  public Facets configure(Consumer<Binder> configure) {
    // this passing looks weird, but we reuse nonOverlappingConfigure
    // for section control, but then we just store configure into
    // servicelet module configuration, not using it as facets above
    configureNoOverlap(serviceletBlocks::add, configure);
    return this;
  }

  @Override
  public Facets configurePlatform(Consumer<Binder> configure) {
    configureNoOverlap(platformBlocks::add, configure);
    return this;
  }

  protected final HttpFacet http = new HttpFacet() {
    @Override
    public <I> RequireEndpoint<I> require(Key<I> key) {
      StackTraceElement source = getCallingSource(this);

      Manifest.Resource resource = new Manifest.Resource.Builder()
          .reference(References.reference(key))
          .kind(Manifest.Kind.HTTP_REQUIRE)
          .atLine(getLine(source))
          .build();

      declaration.addResources(resource);

      Tripwire tripwire = new Tripwire(this)
          .moreThanOnce("Don't call .unbound() more than once on %s", format(resource))
          .enque(platformBlocks::add);

      serviceletBlocks.add(binder -> {
        if (tripwire.never()) {
          binder.bind(key)
              .toProvider(Jaxrs.proxyProvider(key))
              .in(Scopes.SINGLETON);
        }
      });

      return new RequireEndpoint<>() {
        @Override
        public void unbound() {
          tripwire.clear();
        }

        @SuppressWarnings("unchecked")
        @Override
        public I get() {
          var afterInjected = new AfterInjected<>(key);
          onInjectorBlocks.add(afterInjected);
          return (I) Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[]{key.getTypeLiteral().getRawType()},
              new DelegatingProxyHandler<>(afterInjected));
        }
      };
    }

    @Override
    public <I> ProvideEndpoint<I> provide(Key<I> key) {
      StackTraceElement source = getCallingSource(this);

      Manifest.Resource resource = new Manifest.Resource.Builder()
          .reference(References.reference(key))
          .kind(Manifest.Kind.HTTP_PROVIDE)
          .atLine(getLine(source))
          .build();

      declaration.addResources(resource);

      serviceletBlocks.add(b -> {
        Multibinder.newSetBinder(b.withSource(source), Object.class, Jaxrs.Registered.class)
            .addBinding()
            .to(key);
      });

      Tripwire tripwire = new Tripwire(this)
          .never("Please provide implementation binding using .bind*() on %s", format(resource))
          .moreThanOnce("Cannot have more than once using .bind*() on %s", format(resource))
          .enque(platformBlocks::add);

      return new ProvideEndpoint<>() {
        @Override
        public void bind(Consumer<LinkedBindingBuilder<I>> configure) {
          if (tripwire.clear()) {
            StackTraceElement bindSource = getCallingSource(this);
            serviceletBlocks.add(b -> configure.accept(
                b.withSource(bindSource).bind(key)));
          }
        }

        @Override
        public void unbound() {
          tripwire.clear();
        }
      };
    }

    @Override
    public String toString() {
      // tailored to be put as "method name" in StackTraceElement (so IDE links would still work)
      return name + "->http";
    }
  };

  protected final StreamsFacet stream = new StreamsFacet() {

    @Override
    public <R> ProduceRecords<R> produce(Key<R> key) {
      StackTraceElement source = getCallingSource(this);

      Manifest.Resource resource = new Manifest.Resource.Builder()
          .reference(References.reference(key))
          .kind(Manifest.Kind.STREAM_PRODUCE)
          .atLine(getLine(source))
          .build();

      declaration.addResources(resource);

      platformBlocks.add(b -> {
        Multibinder.newSetBinder(b.withSource(source), TopicImport.class)
            .addBinding()
            .toInstance(TopicImport.of(key));
      });

      Tripwire tripwire = new Tripwire(this)
          .moreThanOnce("Don't call .unbound() more than once on %s", format(resource))
          .enque(platformBlocks::add);

      Key<Sender<R>> senderKey = Streams.toSenderKey(key);

      serviceletBlocks.add(binder -> {
        if (tripwire.never()) {
          binder.bind(senderKey)
              .toProvider(Streams.senderProvider(key))
              .in(Scopes.SINGLETON);
        }
      });

      return new ProduceRecords<>() {
        @Override
        public void unbound() {
          tripwire.clear();
        }

        @Override
        public Sender<R> sender() {
          AfterInjected<Sender<R>> futureSender = new AfterInjected<>(senderKey);
          onInjectorBlocks.add(futureSender);
          return new Sender<>() {
            @Override
            public Topic topic() {
              return futureSender.get().topic();
            }

            @Override
            public void write(Iterable<R> records) {
              futureSender.get().write(records);
            }

            @Override
            public String toString() {
              return "Sender<" + key + ">";
            }
          };
        }
      };
    }

    @Override
    public <R> ConsumeRecords<R> consume(Key<R> key) {
      StackTraceElement source = getCallingSource(this);

      Manifest.Resource resource = new Manifest.Resource.Builder()
          .reference(References.reference(key))
          .kind(Manifest.Kind.STREAM_CONSUME)
          .atLine(getLine(source))
          .build();

      declaration.addResources(resource);

      platformBlocks.add(b -> {
        Multibinder.newSetBinder(b.withSource(source), TopicImport.class)
            .addBinding()
            .toInstance(TopicImport.of(key));
      });

      Tripwire tripwire = new Tripwire(this)
          .never("Please provide implementation binding using .bind*() on %s", format(resource))
          .moreThanOnce("Cannot have more than once using .bind*() on %s", format(resource))
          .enque(platformBlocks::add);

      return new ConsumeRecords<>() {
        Named allocatedUniqueName = autoName.get();
        Optional<String> group = Optional.empty();

        Key<Receiver<R>> receiverKey = Streams.toReceiverKey(key, allocatedUniqueName);

        @Override
        public GroupConsumeRecords<R> inGroup(String group) {
          this.group = Optional.of(group);
          tripwire.touch();
          return this;
        }

        @Override
        public void bind(Consumer<LinkedBindingBuilder<Receiver<R>>> bindingClosure) {
          if (tripwire.clear()) {
            StackTraceElement bindSource = getCallingSource(stream);
            serviceletBlocks.add(binder -> {
              Binder b = binder.withSource(bindSource);
              // Complete any DSL binding for receiver callback
              bindingClosure.accept(b.bind(receiverKey));
              // Contribute it aa a service so it will be start and begin processing
              ServiceManagerModule.managedServices(b).addBinding()
                  .toProvider(Streams.dispatcherProvider(key, receiverKey, group))
                  .in(Scopes.SINGLETON);
            });
          }
        }

        @Override
        public void unbound() {
          tripwire.clear();
        }
      };
    }

    @Override
    public String toString() {
      return name.value() + "->stream";
    }
  };

  protected final DatabaseFacet database = new DatabaseFacet() {

    @Override
    public <D> void repository(Class<D> repositoryInterface) {
      StackTraceElement source = getCallingSource(name);
      Key<D> key = Key.get(repositoryInterface);

      serviceletBlocks.add(binder -> {
        binder.withSource(source)
            .bind(key)
            .toProvider(Databases.repositoryOwnProvider(key));
      });

      Manifest.Resource resource = new Manifest.Resource.Builder()
          .reference(References.reference(SqlAccessor.class))
          .kind(Manifest.Kind.DATABASE_REQUIRE)
          .atLine(getLine(source))
          .build();

      declaration.addResources(resource);
    }

    /**
     * Registers entity for metadata information purposes only.
     */
    @Override
    public <D> void record(Class<D> recordType) {
      StackTraceElement source = getCallingSource(name);

      Manifest.Resource resource = new Manifest.Resource.Builder()
          .reference(References.reference(recordType))
          .kind(Manifest.Kind.DATABASE_RECORD)
          .atLine(getLine(source))
          .build();

      serviceletBlocks.add(binder -> {
        // TODO Should it stay like that or not?
        // Need to rethink/redo database evolution
        var ddlFilename = recordType.getSimpleName() + ".ddl.sql";
        @Nullable URL ddl = recordType.getResource(ddlFilename);
        if (ddl != null) {
          Multibinder.newSetBinder(binder.withSource(source), DatabaseScript.class)
              .addBinding()
              .toInstance(DatabaseScript.fromResource(recordType, ddlFilename));
        }
      });

      declaration.addResources(resource);
    }

    @Override
    public void init(DatabaseScript script) {
      StackTraceElement source = getCallingSource(name);
      serviceletBlocks.add(binder -> {
        Multibinder.newSetBinder(binder.withSource(source), DatabaseScript.class)
            .addBinding()
            .toInstance(script);
      });
    }

    @Override
    public String toString() {
      return name.value() + "->database";
    }
  };

  public Servicelet toServicelet() {
    // making earliest up-front construction/validation
    Manifest manifest = declaration.build();
    Module module = createModule(manifest);
    return new Servicelet() {
      @Override
      public Manifest manifest() {
        return manifest;
      }

      @Override
      public Module module() {
        return module;
      }

      @Override
      public String toString() {
        return Servicelet.class.getSimpleName() + "[" + name.value() + "]";
      }
    };
  }

  private Module createModule(Manifest manifest) {
    return binder -> {
      // will act as default source if not overridden in blocks
      StackTraceElement source = getCallingSource(name);

      Binder platformBinder = binder.skipSources(Facets.class).withSource(source);
      // (experimental) let platform know about manifests of servicelets running on it
      MapBinder.newMapBinder(platformBinder, Servicelet.Name.class, Manifest.class)
          .addBinding(name)
          .toInstance(manifest);
      // Replay all captured configure blocks for platform binder
      for (Consumer<Binder> pb : platformBlocks) {
        pb.accept(platformBinder);
      }
      // Here we create a single servicelet definition unique to this manifest.
      // Each servicelet module corresponds to a separate servicelet injector
      Launcher.servicelet(b -> {
        Binder serviceletBinder = b.withSource(source);
        // TODO get rid of it in favor of reading name from manifest
        ServiceletNameModule.assignName(serviceletBinder, name);
        // making manifest instance available inside servicelet
        serviceletBinder.bind(Manifest.class).toInstance(manifest);

        // both platform and servicelet-specific binder
        for (Consumer<Binder> sb : serviceletBlocks) {
          sb.accept(serviceletBinder);
        }

        Launcher.serviceletInjected(serviceletBinder).addBinding().toInstance(injector -> {
          for (Consumer<Injector> block : onInjectorBlocks) {
            block.accept(injector);
          }
        });

        // pass-through module.configure(binder) but not binder.install(module),
        // avoids excessive "hierarchies" of recording binders inside Guice
      }).configure(platformBinder);
    };
  }

  private static final class DelegatingProxyHandler<T> extends AbstractInvocationHandler {
    private final Supplier<T> supplier;

    DelegatingProxyHandler(Supplier<T> supplier) {
      this.supplier = supplier;
    }

    @Override protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        return method.invoke(supplier.get(), args);
      } catch (InvocationTargetException ex) {
        throw ex.getCause();
      }
    }

    @Override public String toString() {
      return "DelegatingProxy<" + supplier + ">";
    }
  }

  private static final class AfterInjected<T> implements Supplier<T>, Consumer<Injector> {
    private final Key<T> key;
    private volatile T instance;

    AfterInjected(Key<T> key) {
      this.key = key;
    }

    @Override public T get() {
      @Nullable T t = instance;
      if (t == null) throw new IllegalStateException("Servicelet not injected yet: " + key);
      return t;
    }

    @Override public void accept(Injector injector) {
      instance = injector.getInstance(key);
    }

    @Override public String toString() {
      @Nullable T t = instance;
      return t != null
          ? ("AfterInjected(" + t + ")")
          : ("AfterInjected<" + key + ">(uninitialized)");
    }
  }

  private void tryAttachSource() {
    StackTraceElement source = getCallingSource(name);
    @Nullable String fileName = source.getFileName();
    // weed out unavailable filename or defaulted stack trace element
    if (fileName == null || fileName.equals(Facets.class.getSimpleName() + ".java")) return;

    Manifest.SourceInfo.Builder builder = new Manifest.SourceInfo.Builder()
        .file(fileName);

    // Just in case we've packaged source file as resource, and this should be the case for quality
    // packaging of servicelet.
    @Nullable URL resource = getClass().getResource(fileName);
    if (resource != null) {
      try {
        Resources.asCharSource(resource, StandardCharsets.UTF_8)
            .forEachLine(builder::addLines);
      } catch (IOException ex) {
        // We cannot spuriosly fail here with exception, so we just dumping error
        // as a source so we can see it and fix
        // occurrences of these kind of exceptions are extemely rare
        // so I don't expect to ever see it
        builder.addAllLines(Splitter.on('\n').split(ex.toString()));
      }
    }

    declaration.source(builder.build());
  }

  protected <S> void configureNoOverlap(Consumer<S> configure, S section) {
    if (semaphore.tryAcquire()) {
      try {
        configure.accept(section);
      } finally {
        semaphore.release();
      }
    } else {
      StackTraceElement source = getCallingSource(section);
      platformBlocks.add(b -> {
        b.withSource(source)
            .addError("Section %s overlaps in servicelet DSL, avoid nesting one section in another", section);
      });
    }
  }

  /**
   * Utility to detect dangling incomplete definitions and/or configuration methods that improperly called more than
   * once.
   */
  private final class Tripwire implements Consumer<Binder> {
    private final Object facet;

    private int count;
    private Object source;
    private @Nullable String neverError;
    private @Nullable String moreThanOnceError;

    Tripwire(Object facet) {
      this.facet = facet;
      this.source = getCallingSource(facet);
    }

    Tripwire never(String message, Object... arguments) {
      neverError = Messages.format(message, arguments);
      return this;
    }

    Tripwire moreThanOnce(String message, Object... arguments) {
      // use Guice's formatting (specials for Key, Member, Elements etc)
      moreThanOnceError = Messages.format(message, arguments);
      return this;
    }

    /**
     * The state of being never called, together with absent error message for never, can be used for negative disabling
     * configuration. i.e. precondition on being {@link #never()} while disabling action would call {@link #clear()}.
     */
    boolean never() {
      return count == 0;
    }

    /**
     * Predicate on this to do action only if cleared. Will return false if it's an error to call it more than once.
     */
    boolean clear() {
      touch();
      return ++count == 1 || moreThanOnceError == null;
    }

    /**
     * Updates source from calling stack, but doesn't influence the state of tripwire
     */
    void touch() {
      source = getCallingSource(facet);
    }

    /**
     * This is expected to be queued into list of tripwires (or just binding processors) running at appropriate time
     * (after any expected .clear() would be called.
     */
    Tripwire enque(Consumer<? super Tripwire> consumer) {
      consumer.accept(this);
      return this;
    }

    @Override
    public void accept(Binder binder) {
      if (neverError != null && count == 0) {
        binder.addError(new Message(source, neverError));
      }
      if (moreThanOnceError != null && count > 1) {
        binder.addError(new Message(source, moreThanOnceError));
      }
    }
  }

  private Supplier<Named> autoNameSupplier() {
    AtomicInteger counter = new AtomicInteger();
    return () -> Names.named(name + "-" + counter.getAndIncrement());
  }

  /**
   * Returns the stack frame which is most nested, but outside of this class (and it's inner class friends). If cannot
   * find suitable stack trace, the top caller is returned just to avoid returning and handling {@code null}.
   */
  protected StackTraceElement getCallingSource(Object overrideLambdaMethod) {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    for (StackTraceElement e : stackTrace) {
      if (skipSourcesPrefixed.stream().noneMatch(p -> e.getClassName().startsWith(p))) {
        // This cosmetics is an overkill, maybe
        boolean isJavaLambda = e.getMethodName().contains("lambda$")
            || e.getMethodName().equals("invoke");

        return new StackTraceElement(
            e.getClassName(),
            isJavaLambda ? overrideLambdaMethod.toString() : e.getMethodName(),
            e.getFileName(),
            e.getLineNumber());
      }
    }
    assert stackTrace.length > 1 : "someone is calling this method, right?";
    return stackTrace[1];
  }

  protected static String format(Manifest.Resource resource) {
    String asPath = resource.kind().name().replace('_', '.').toLowerCase();
    return Messages.format("%s<%s>", asPath, resource.reference());
  }

  private static OptionalInt getLine(StackTraceElement stackFrame) {
    return stackFrame.getLineNumber() >= 0
        ? OptionalInt.of(stackFrame.getLineNumber())
        : OptionalInt.empty();
  }

  @FunctionalInterface
  public interface AllSections {
    void configure(HttpFacet http, StreamsFacet stream, DatabaseFacet database, Consumer<Module> configure);
  }

  public static Servicelet servicelet(String name, AllSections sections) {
    Facets facets = new Facets(name);
    var modules = new ArrayList<Module>();
    sections.configure(facets.http, facets.stream, facets.database, modules::add);
    for (Module m : modules) {
      // we cannot pass facets.serviceletBlocks into sections.configure,
      // because concurrent modification happens
      facets.serviceletBlocks.add(m::configure);
    }
    return facets.toServicelet();
  }

  public static Consumer<Binder> healthy(Runnable runnable) {
    return binder -> ServiceManagerModule.managedListeners(binder).addBinding()
        .toInstance(new ServiceManager.Listener() {
          @Inject Injector injector;

          @Override public void healthy() {
            injector.injectMembers(runnable);
            new Thread(runnable, "onHealthy").start();
          }
        });
  }
}
