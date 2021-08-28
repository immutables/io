package io.immutables.micro.wiring;

import io.immutables.codec.OkJson;
import io.immutables.micro.kafka.KafkaModule;
import okio.Okio;
import io.immutables.micro.Launcher;
import io.immutables.micro.Manifest;
import io.immutables.micro.Servicelet;
import io.immutables.micro.creek.BrokerModule;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Injector;

public final class MainLauncher {
  private final String[] arguments;
  private final List<Servicelet> servicelets = new ArrayList<>();

  public MainLauncher(String... arguments) {
    this.arguments = arguments.clone();
  }

  public MainLauncher use(Servicelet... servicelet) {
    Collections.addAll(servicelets, servicelet);
    return this;
  }

  public void run() {
    if (Arrays.equals(arguments, new String[]{ARG_PRINT_MANIFEST})) {
      OkJson json = new Launcher()
          .add(new JsonModule())
          .inject()
          .getInstance(OkJson.class);

      List<Manifest> manifests = servicelets.stream()
          .map(Servicelet::manifest)
          .collect(Collectors.toUnmodifiableList());

      var codec = json.get(new TypeToken<List<Manifest>>() {});
      json.toJson(manifests, Okio.buffer(Okio.sink(System.out)), codec);
      return;
    }

    Launcher launcher = new Launcher()
        .add(new ExposeMainArguments(arguments))
        .add(new MainSetupModule())
        .add(new LoadSetupModule())
        .add(new ServiceletNameModule())
        .add(new ServiceManagerModule())
        .add(new JsonModule())
        .add(new JaxrsModule())
        .add(new JaxrsRemoteModule())
        .add(new DatabaseModule())
        .add(new ClockModule())
        //.add(new KafkaModule())// KafkaModule() //KafkaHttpModule()//new BrokerModule()
        .add(Set.of(arguments).contains(ARG_KAFKA) ? new KafkaModule() :  new BrokerModule())
        .add(new ExceptionPrinting())
        .add(new MicroInfoModule())
//        .add(new ManifestCatalogExport()) //TODO fixme(or fixed)? StackOverflowError at io.immutables.micro.spect.CatalogCollector.extractType(CatalogCollector.java:179)
        .add(new FactReporting());

    for (Servicelet s : servicelets) {
      launcher.add(s.module());
    }

    Injector injector = launcher.inject();

    ServiceManager manager = injector.getInstance(ServiceManager.class);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
      } catch (TimeoutException ex) {
        System.err.println("Timeout on shutdown, just exiting");
      } catch (Exception ex) {
        System.err.println("Exception on shutdown, just exiting");
        ex.printStackTrace();
      }
    }, "stop-services-on-shutdown"));

    manager.startAsync().awaitHealthy();
  }

  public static final String ARG_KAFKA = "--kafka";
  public static final String ARG_PRINT_MANIFEST = "--print-manifest";
}
