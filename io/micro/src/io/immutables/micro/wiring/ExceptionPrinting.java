package io.immutables.micro.wiring;

import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Systems;
import com.google.inject.Binder;
import com.google.inject.Module;

/** Temporary solution, better reporting will come with logs/metrics integration. */
@Deprecated
final class ExceptionPrinting implements Module {
  @Override
  public void configure(Binder binder) {
    ExceptionSink sink = ExceptionSink.printingStackTrace(System.err);
    binder.bind(ExceptionSink.class).toInstance(sink);
    binder.bind(ExceptionSink.class).annotatedWith(Systems.Shared.class).toInstance(sink);
  }
}
