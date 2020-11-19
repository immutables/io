package io.immutables.micro.kafka;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.BooleanSupplier;
import static java.lang.System.currentTimeMillis;

// TODO kill or change this abstraction
@Deprecated
public final class Await {
  private final Duration duration;
  private final long retryMillis;

  private Await(Duration duration, long retryMillis) {
    this.duration = duration;
    this.retryMillis = retryMillis;
  }

  public static Await await(int amount, ChronoUnit unit) {
    return new Await(Duration.of(amount, unit), 50);
  }

  public static Await await(int amount, ChronoUnit unit, long retryMillis) {
    return new Await(Duration.of(amount, unit), retryMillis);
  }

  public void doWait() {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void until(BooleanSupplier condition) {
    long start = currentTimeMillis();
    while (!condition.getAsBoolean()) {
      if (currentTimeMillis() - start > duration.toMillis()) {
        throw new RuntimeException(String.format("Condition not meet within %s ms", duration.toMillis()));
      }
      try {
        Thread.sleep(retryMillis);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
