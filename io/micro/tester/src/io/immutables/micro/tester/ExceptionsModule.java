package io.immutables.micro.tester;

import io.immutables.micro.Systems;
import io.immutables.micro.ExceptionSink;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Qualifier;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Module that defines sink capturing unhandled exceptions.
 * Tester infrastructure injects mutable (and thread-safe)
 * {@code @UnhanledDuringTestCase List<Throwable>} to inspect and/or propagate
 * exceptions, clears list afterwards.
 */
final class ExceptionsModule extends AbstractModule {
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface UnhanledDuringTestCase {}

  @Provides
  @Singleton
  public @UnhanledDuringTestCase List<Throwable> exceptions() {
    return new CopyOnWriteArrayList<>();
  }

  @Provides
  @Singleton
  public ExceptionSink sink(@UnhanledDuringTestCase List<Throwable> exceptions) {
    return ExceptionSink.collectingUnhandled(exceptions::add);
  }

  @Provides
  public @Systems.Shared ExceptionSink sharedSink(ExceptionSink sink) {
    return sink;
  }
}
