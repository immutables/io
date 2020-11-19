package io.immutables.micro.wiring;

import io.immutables.micro.Systems;
import java.time.Clock;
import javax.inject.Singleton;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public final class ClockModule extends AbstractModule {
  @Provides
  @Singleton
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Provides
  @Singleton
  public @Systems.Shared Clock sharedClock(Clock clock) {
    return clock;
  }
}
