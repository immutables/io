package io.immutables.micro.tester;

import io.immutables.micro.Jaxrs;
import io.immutables.micro.Streams;
import io.immutables.micro.Databases;
import io.immutables.micro.Origin;

import com.google.common.net.HostAndPort;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/** Database, Endpoint resolution and other platform configurations for tester. */
final class SetupModule extends AbstractModule {
  @Provides
  @Singleton
  public Databases.Setup databases() {
    // we use own per-test instance random suffix instead of
    // per database random suffix, so all test databases have the same suffix
    String database = String.format("%s__test%d",
        Databases.Setup.DATABASE_SERVICELET,
        Math.abs((int) (Math.random() * Integer.MAX_VALUE)));

    return new Databases.Setup.Builder()
        .origin(here)
        .connect("postgresql://localhost/postgres")
        .autostart(true)
        .mode(Databases.Setup.Mode.CREATE_AND_DROP)
        .database(database)
        .build();
  }

  @Provides
  @Singleton
  public Jaxrs.Setup jaxrs() {
    return new Jaxrs.Setup.Builder()
        .origin(here)
        .listen(HostAndPort.fromParts("localhost", 0))
        .resolveInPlatform(true)
        .build();
  }

  @Provides
  @Singleton
  public Streams.Setup streams() {
    return new Streams.Setup.Builder()
        .origin(here)
        .hostPort(HostAndPort.fromHost("localhost"))
        .autostart(true)
        .topicMode(Streams.Setup.Mode.CREATE_AND_DROP)
        .shards(1)
        .build();
  }

  private static final Origin here = new Origin.Builder()
      .resource(SetupModule.class.getName())
      .isFallback(true)
      .fallbackInfo("tester embedded setup")
      .build();
}
