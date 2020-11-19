package io.immutables.micro.tester;

import io.immutables.micro.Launcher;
import io.immutables.micro.Servicelet;
import io.immutables.micro.creek.BrokerModule;
import io.immutables.micro.kafka.KafkaModule;
import io.immutables.micro.stream.http.kafka.KafkaHttpModule;
import io.immutables.micro.wiring.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Injector;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * Runner was almost the only way to implement proper fixture initialization in JUnit4. We need to
 * create fixture instance and handle it on each lifecycle phase, including injecting each test
 * object instance.
 * Also, by creating a runner and using {@link BlockJUnit4ClassRunner} as a base we're running
 * all tests serially (non parallel) which suits the purpose also (a we share the same temporary
 * databases).
 */
public final class ServiceletTester extends BlockJUnit4ClassRunner {

  public ServiceletTester(Class<?> testSuiteClass) throws InitializationError {
    super(testSuiteClass);
  }

  @Override
  protected Statement withBeforeClasses(Statement statement) {
    Statement beforeClasses = super.withBeforeClasses(statement);
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        fixture().start();
        fixture().beforeAll();
        beforeClasses.evaluate();
      }
    };
  }

  @Override
  protected Statement withAfterClasses(Statement statement) {
    Statement afterClasses = super.withAfterClasses(statement);
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        // we use autoclosable for proper try/finally/addSuppressed handling
        // this is relevant only for 'after' hooks, 'before' can fail more easily
        // reminder: close performed on resources in reverse order,
        // first we call afterAll, then
        try (AutoCloseable stopTester = fixture()::stop;
            AutoCloseable afterAll = fixture()::afterAll) {
          afterClasses.evaluate();
        }
      }
    };
  }

  @Override
  protected Statement withBefores(FrameworkMethod method, Object target, Statement statement) {
    Statement befores = super.withBefores(method, target, statement);
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        fixture().beforeEach();
        befores.evaluate();
      }
    };
  }

  @Override
  protected Statement withAfters(FrameworkMethod method, Object target, Statement statement) {
    Statement withAfters = super.withAfters(method, target, statement);
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        // we use autoclosable for proper try/finally/addSuppressed handling
        try (AutoCloseable c = fixture()::afterEach) {
          try {
            withAfters.evaluate();
          } catch (Throwable ex) {
            fixture().propagateUnhandledExceptions(ex);
            // below code would be unreachable as propagateUnhandledExceptions must
            // rethrow exception (adding any unhandled exceptions as suppressed)
            throw new AssertionError("unreachable");
          }
          // if test case did not throw any exceptions, we still will blow up
          // on unhandled exceptions (if any)
          fixture().propagateUnhandledExceptions();
        }
      }
    };
  }

  @Override
  protected Fixture createTestClass(Class<?> testClass) {
    return new Fixture(testClass);
  }

  private Fixture fixture() {
    return (Fixture) getTestClass();
  }

  @Override
  protected Object createTest() throws Exception {
    Object test = super.createTest();
    fixture().inject(test);
    return test;
  }

  private class Fixture extends TestClass {
    private Injector testerInjector;
    @Inject ServiceManager manager;
    @Inject Set<TesterFacets.When> hooks = Set.of();
    @Inject @ExceptionsModule.UnhanledDuringTestCase List<Throwable> exceptions;

    Fixture(Class<?> testClass) {
      super(testClass);
    }

    void start() throws Exception {
      Facets tester = new Facets(getName());

      configure(tester.facets);

      Servicelet testerServicelet = tester.toServicelet();
      Injector platformInjector = launcher(testerServicelet, tester.underTest, tester.broker).inject();
      platformInjector.injectMembers(this);

      manager.startAsync().awaitHealthy();

      testerInjector = platformInjector.getInstance(ServiceletNameModule.Lookup.class)
          .injectorBy(testerServicelet.name())
          .orElseThrow(AssertionError::new);
    }

    private void configure(TesterFacets facets) throws Exception {
      Method method = getJavaClass().getMethod("init", TesterFacets.class);
      // It allows that the init method can be called as a static or as a regular instance method.
      Object receiver = (method.getModifiers() & Modifier.STATIC) == 0
          ? ServiceletTester.super.createTest()
          : null;

      try {
        method.invoke(receiver, facets); // ignore return value, can be void
      } catch (InvocationTargetException ex) {
        Throwables.propagateIfPossible(ex.getCause(), Exception.class);
        throw ex;
      }
    }

    void inject(Object object) {
      testerInjector.injectMembers(object);
    }

    void beforeAll() {
      hooks.forEach(TesterFacets.When::beforeAll);
    }

    void beforeEach() {
      hooks.forEach(TesterFacets.When::beforeEach);
    }

    void afterEach() {
      hooks.forEach(TesterFacets.When::afterEach);
    }

    void afterAll() {
      hooks.forEach(TesterFacets.When::afterAll);
    }

    // this method was separated out from afterAll due to specifics
    // of exception handling using try-with-resources
    void stop() {
      if (manager == null) return; // not started, failed at construction
      manager.stopAsync().awaitStopped();
    }

    void propagateUnhandledExceptions() throws Throwable {
      if (!exceptions.isEmpty()) {
        Throwable first = exceptions.remove(0);
        AssertionError error = new AssertionError("Unhandled exception occurred", first);
        // this error's stack trace is irrelevant, so we truncate it, producing less clutter
        error.setStackTrace(new StackTraceElement[0]);
        exceptions.forEach(error::addSuppressed);
        exceptions.clear();
        throw error;
      }
    }

    void propagateUnhandledExceptions(Throwable throwable) throws Throwable {
      if (!exceptions.isEmpty()) {
        exceptions.forEach(throwable::addSuppressed);
        exceptions.clear();
      }
      throw throwable;
    }
  }

  private static Launcher launcher(Servicelet tester, Set<Servicelet> testees, TesterFacets.Broker broker) {
    Launcher launcher = new Launcher()
        .add(new ClockModule())
        .add(new SetupModule())
        .add(new ServiceletNameModule())
        .add(new ServiceManagerModule())
        .add(new JsonModule())
        .add(new JaxrsModule())
        .add(new JaxrsRemoteModule())
        .add(new DatabaseModule())
        .add(new ExceptionsModule())
        .add(tester.module());

    if (broker == TesterFacets.Broker.IN_MEMORY) {
      launcher.add(new BrokerModule());
      launcher.add(new BrokerTestModule());
    }
    if (broker == TesterFacets.Broker.KAFKA) {
      launcher.add(new KafkaModule());
    }
    if (broker == TesterFacets.Broker.PROXY) {
      launcher.add(new KafkaHttpModule());
    }

    for (Servicelet t : testees) {
      launcher.add(t.module());
    }

    return launcher;
  }
}
