package io.immutables.micro;

import java.io.PrintStream;
import java.util.function.Consumer;

/**
 * The final destination for exceptions which are not just rethrown. In exchange for exception sent to the sink, caller
 * receives exception token which caller might use to, for example, print as reference in error response. Note, this
 * class works with {@link Throwable}s but often we calls them "exceptions" here, please don't ask us to rename it to
 * "ThrowableSink" for example.
 */
public interface ExceptionSink {
  /**
   * Report fully consumed exception and action taken (other than rethrow), this includes judiciously ignored exception.
   * Don't use this method on the exception you rethrow, only on the ones which are considered to be "handled" and to be
   * disposed. This method must not throw any exception, in worst case the implementation can return phony token.
   * @param exception handled and consumed exception
   * @return token externalized reference to exception
   */
  ExceptionToken consumed(Throwable exception);

  /**
   * Report unhandled exception, usually somewhere at the top level of execution. Avoid sending exception in responses
   * (and even logs) opting to joining this information from the data stream ("exception queue") produced by handler
   * like this. This method must not throw any exception, in worst case the implementation can return phony token, but
   * try best to record exception.
   * @param exception unhandled exception
   * @return token externalized reference to exception
   */
  ExceptionToken unhandled(Throwable exception);

  /**
   * Simplistic exception handler which prints exceptions to syserr.
   */
  static ExceptionSink printingStackTrace(PrintStream writer) {
    return new ExceptionSink() {
      {
        // FIXME special case or find the other way - we try to force classloading of ImmutableExceptionToken
        // early so it will not fall victim of "too many open files" when such condition
        // occurs, we cannot then even report such condition.
        ImmutableExceptionToken.of(0).getValue();
      }
      @Override
      public ExceptionToken consumed(Throwable exception) {
        ExceptionToken token = ExceptionToken.random();
        writer.printf("Consumed %s%n", token);
        exception.printStackTrace(writer);
        return token;
      }

      @Override
      public ExceptionToken unhandled(Throwable exception) {
        ExceptionToken token = ExceptionToken.random();
        writer.printf("Unhandled %s%n", token);
        exception.printStackTrace(writer);
        return token;
      }

      @Override
      public String toString() {
        return ExceptionSink.class.getSimpleName() + ".printingStackTrace()";
      }
    };
  }

  static ExceptionSink collectingUnhandled(Consumer<Throwable> collection) {
    return new ExceptionSink() {
      @Override public ExceptionToken consumed(Throwable exception) {
        return ExceptionToken.random();
      }

      @Override public ExceptionToken unhandled(Throwable exception) {
        collection.accept(exception);
        return ExceptionToken.random();
      }
    };
  }

  static ExceptionSink assertNoUnhandled() {
    return new ExceptionSink() {
      @Override
      public ExceptionToken consumed(Throwable exception) {
        return ExceptionToken.random();
      }

      @Override
      public ExceptionToken unhandled(Throwable exception) {
        throw new AssertionError(exception);
      }
    };
  }

  /**
   * Turns {@link ExceptionSink} into {@link java.lang.Thread.UncaughtExceptionHandler}. Any uncaught exception or error
   * will be always wrapped into {@link RuntimeException} with attached message containing thread name.
   */
  static Thread.UncaughtExceptionHandler asUncaughtHandler(ExceptionSink sink) {
    return (t, e) -> sink.unhandled(new Error("Uncaught in thread: " + t.getName(), e));
  }
}

