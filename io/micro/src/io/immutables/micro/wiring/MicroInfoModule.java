package io.immutables.micro.wiring;

import io.immutables.codec.OkJson;
import io.immutables.micro.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.immutables.data.Data;
import org.immutables.value.Value;

@Data
@Value.Enclosing
public class MicroInfoModule extends AbstractModule {

  public interface MicroInfoScanner {
    Collection<MicroInfo> scan();
  }

  @Value.Immutable
  public interface Setup extends Origin.Traced<Setup> {
    boolean expose();

    default @Value.Default String directory() {
      return "";
    }

    class Builder extends ImmutableMicroInfoModule.Setup.Builder {}
  }

  @Override
  protected void configure() {
    bind(MicroInfoDirectory.class).in(Singleton.class);
  }

  public static class MicroInfoDirectory extends AbstractScheduledService implements MicroInfoScanner {
    @Inject ExceptionSink exceptions;
    @Inject OkJson json;
    @Inject Setup setup;
    @Inject AtomicReference<MicroInfo> microInfo;

    @Nullable JsonEntriesDirectory<String, MicroInfo> directory;

    boolean enabled() { return !setup.directory().isEmpty(); }

    @Override protected Scheduler scheduler() {
      return Scheduler.newFixedDelaySchedule(5, 5, TimeUnit.SECONDS);
    }

    @Override protected void startUp() {
      if (enabled()) {
        directory = new JsonEntriesDirectory<>(
            json,
            json.get(MicroInfo.class),
            exceptions,
            setup.origin(),
            setup.directory(),
            MicroInfo::key);

        microInfo().ifPresent(m -> directory.store(m));
      }
    }

    private Optional<MicroInfo> microInfo() {
      return Optional.ofNullable(microInfo.get());
    }

    @Override protected void runOneIteration() {
      assert enabled() && directory != null : "not registered as service if not enabled";
      microInfo().ifPresent(m -> directory.store(m, true));
    }

    @Override public Collection<MicroInfo> scan() {
      return directory != null
          ? directory.scan()
          : microInfo().map(Set::of).orElseGet(Set::of);
    }
  }

  @ProvidesIntoSet
  public ServiceManager.Listener populateMicroInfo(
      Setup setup,
      AtomicReference<MicroInfo> info,
      @Jaxrs.Registered HostAndPort listen,
      @Systems ConcurrentMap<Servicelet.Name, Injector> servicelets,
      DatabaseModule.ConnectionInfo connectionInfo,
      @Systems Map<Servicelet.Name, DatabaseModule.DatabaseForServicelet> databases) {
    return new ServiceManager.Listener() {
      @Override public void healthy() {
        if (setup.expose()) {
          RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

          info.set(new MicroInfo.Builder()
              .pid(runtime.getPid())
              .at(runtime.getName().replaceAll("^[0-9]+@", ""))
              .listen(listen)
              .origin(setup.origin())
              .servicelets(servicelets.keySet())
              .databases(databases())
              .build());
        }
      }

      List<MicroInfo.DatabaseInfo> databases() {
        return databases.values().stream().map(db ->
            new MicroInfo.DatabaseInfo.Builder()
                .connection(connectionInfo.connect())
                .servicelet(db.servicelet())
                .database(db.database())
                .isolate(db.setup().isolate())
                .build())
            .collect(Collectors.toList());
      }
    };
  }

  @Provides @Singleton
  public AtomicReference<MicroInfo> microInfo() {
    return new AtomicReference<>();
  }

  @Provides @Singleton
  public MicroInfoScanner infoScanner(MicroInfoDirectory directory) {
    return directory;
  }

  @ProvidesIntoSet
  public Service runtimeInfoPublisher(MicroInfoDirectory directory) {
    return directory.enabled() ? directory : ServiceManagerModule.noop();
  }
}
