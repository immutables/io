package io.immutables.micro.wiring;

import io.immutables.micro.*;

import io.immutables.micro.Databases.Setup.Mode;
import java.util.Map;
import com.google.common.net.HostAndPort;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

final class MainSetupModule extends AbstractModule {

  @Provides
  @Singleton
  public Jaxrs.Setup jaxrs(SetupLoader loader) {
    return loader.load(Jaxrs.Setup.class, "jaxrs", MainSetupModule::fallbackJaxrs);
  }

  @Provides
  @Singleton
  public Streams.Setup streams(SetupLoader loader, Map<Servicelet.Name, Manifest> manifests) {
    return loader.load(Streams.Setup.class, "streams",
        () -> fallbackStreams(Streams.requiresStream(manifests.values())));
  }

  @Provides
  @Singleton
  public Databases.Setup databases(SetupLoader loader, Map<Servicelet.Name, Manifest> manifests) {
    return loader.load(Databases.Setup.class, "database",
        () -> fallbackDatabase(Databases.requiresDatabase(manifests.values())));
  }

  @Provides
  @Singleton
  public MicroInfoModule.Setup microInfo(SetupLoader loader) {
    return loader.load(MicroInfoModule.Setup.class, "microInfo", MainSetupModule::fallbackMicroInfo);
  }

  @Provides
  @Singleton
  public ManifestCatalogExport.Setup manifestExport(SetupLoader loader) {
    return loader.load(ManifestCatalogExport.Setup.class, "manifestExport", MainSetupModule::fallbackManifestExport);
  }

  private static Databases.Setup fallbackDatabase(boolean requiresDatabase) {
    return new Databases.Setup.Builder()
        .connect("jdbc:postgresql://localhost/postgres")
        .database("<servicelet>")
        .autostart(requiresDatabase)
        .mode(Mode.CREATE_IF_NOT_EXIST)
        .build();
  }

  private static Jaxrs.Setup fallbackJaxrs() {
    return new Jaxrs.Setup.Builder()
        .listen(HostAndPort.fromParts("localhost", 0))
        .resolveInDirectory("~/.micro/endpoints")
        .resolveInPlatform(true)
        .build();
  }

  private static Streams.Setup fallbackStreams(boolean requiresStream) {
    return new Streams.Setup.Builder()
        .hostPort(HostAndPort.fromHost("localhost"))
        .autostart(requiresStream)
        .topicMode(Streams.Setup.Mode.CREATE_IF_NOT_EXIST)
        .shards(1) // FIXME make the other default 12 or 24 as default
        .build();
  }

  private static MicroInfoModule.Setup fallbackMicroInfo() {
    return new MicroInfoModule.Setup.Builder()
        .expose(true)
        .directory("~/.micro/runtimes")
        .build();
  }

  private static ManifestCatalogExport.Setup fallbackManifestExport() {
    return new ManifestCatalogExport.Setup.Builder()
        .directory("~/.micro/manifests")
        .build();
  }
}
