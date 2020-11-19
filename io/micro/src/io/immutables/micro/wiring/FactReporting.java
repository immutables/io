package io.immutables.micro.wiring;

import io.immutables.micro.*;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Provider;
import javax.inject.Singleton;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.internal.Errors;
import com.google.inject.multibindings.ProvidesIntoSet;

/**
 * This can evolve in a system better integrated with distributed logging/metrics and tracing.
 */
@Deprecated
// TODO refactor this crap
// Should be inverted, when modules contribute to generic reporter
// currently this is way to tight coupling
// maybe switch to ProvidesIntoSet to contribute facts
// as a prerequisite for that it's good to have better, more fleshed
// out API in line with whatever used for metrics.
public class FactReporting extends AbstractModule {
  // approximate time when starting
  private final Stopwatch stopwatch = Stopwatch.createStarted();

  @Provides
  @Singleton
  public List<Facts> facts(
      @Systems LoadSetupModule.SharedConf conf,
      Map<Servicelet.Name, Manifest> manifests,
      @JaxrsRemoteModule.InPlatform Map<Key<?>, Jaxrs.EndpointEntry> inPlatformEndpoints,
      @JaxrsRemoteModule.Resolved Map<Key<?>, Jaxrs.EndpointEntry> resolvedEndpoints,
      @JaxrsRemoteModule.Discovered Set<Jaxrs.EndpointEntry> discoveredEndpoints,
      @Systems Map<Servicelet.Name, DatabaseModule.DatabaseForServicelet> perServiceletDatabases,
      AtomicReference<MicroInfo> microInfo,
      Provider<ServiceManager> manager) {

    Printer printer = new Printer(System.out);

    return ImmutableList.<Facts>builder()
        .add(facts(printer, "servicelet", manifests::keySet))
        .add(facts(printer, "setup", () -> Set.of(conf), obj -> ""))
        .add(facts(printer, "http-provides", inPlatformEndpoints::values, this::formatProvides))
        .add(facts(printer, "http-requires", resolvedEndpoints::values, this::formatRequires))
        .add(facts(printer, "http-discover", () -> discoveredEndpoints, this::formatRequires))
        .add(facts(printer, "database", perServiceletDatabases::values, this::formatDatabase))
        .add(facts(printer, "services", () -> manager.get().servicesByState().entries(), this::formatServices))
        .add(facts(printer, "startup", () -> manager.get().isHealthy()
            ? (Optional.ofNullable(microInfo.get())
            .map(info -> ImmutableMap.of("at", info.at(), "pid", info.pid(), "time", stopwatch))
            .orElseGet(() -> ImmutableMap.of("time", stopwatch)).entrySet())
            : ImmutableSet.of(), this::formatEntries))
        .build();
  }

  @ProvidesIntoSet
  public ServiceManager.Listener startupTimer() {
    return new ServiceManager.Listener() {
      @Override
      public void healthy() {
        stopwatch.stop();
      }
    };
  }

  @ProvidesIntoSet
  public Service job(List<Facts> facts) {
    return new AbstractScheduledService() {
      @Override
      protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(1, 1, TimeUnit.SECONDS);
      }

      @Override
      protected void runOneIteration() throws Exception {
        facts.forEach(Facts::report);
      }

      @Override
      protected String serviceName() {
        return "FactReporter";
      }
    };
  }

  @ThreadSafe
  interface Facts {
    void report();
  }

  private static <F> Facts facts(Printer printer, String caption, Supplier<? extends Collection<F>> getter) {
    return facts(printer, caption, getter, Object::toString);
  }

  private static <F> Facts facts(
      Printer printer,
      String caption,
      Supplier<? extends Collection<F>> getter,
      Function<F, String> formatter) {
    CopyOnWriteArraySet<F> facts = new CopyOnWriteArraySet<>();
    return new Facts() {
      @Override
      public void report() {
        boolean wasEmpty = facts.isEmpty();
        int wasCount = facts.size();
        List<F> newFacts = List.copyOf(getter.get());
        List<F> reportThese = new ArrayList<>();
        for (F f : newFacts) {
          if (facts.add(f)) reportThese.add(f);
        }
        facts.retainAll(newFacts);

        if (!reportThese.isEmpty()) {
          printer.report(caption, reportThese, formatter, !wasEmpty);
        } else if (facts.isEmpty() && wasCount > 0) {
          printer.reportRemoved(caption, wasCount);
        }
      }
    };
  }

  private static final class Printer {
    private final PrintStream out;

    Printer(PrintStream out) {
      this.out = out;
    }

    <F> void report(String caption, Collection<F> facts, Function<F, String> formatter, boolean updated) {
      out.print("\u21aa ");
      out.print(caption);

      if (facts.size() > 1 || updated) {
        out.print(" (");
        if (updated) out.print("+");
        out.print(facts.size());
        out.print(")");
      }
      out.println();

      for (F f : facts) {
        String formatted = formatter.apply(f);
        if (!formatted.isBlank()) {
          out.print(" ");
          out.println(formatted);
        }
        printOrigin(f);
      }

      out.println();
    }

    private void printOrigin(Object object) {
      if (object instanceof Origin.Traced<?>) {
        printOriginLine(((Origin.Traced<?>) object).origin(), "\t\t");
      }
    }

    private void printOriginLine(Origin origin, String indent) {
      if (origin.isUnspecified()) return;

      out.print(indent);

      if (origin.notAvailable()) {
        out.print("\u2718 ");
      } else {
        out.print("\u2714 ");
      }
      out.print(origin.resource());
      if (!origin.innerPath().isEmpty()) {
        out.print("[");
        out.print(origin.innerPath());
        out.print("]");
      }
      if (origin.isFallback()) {
        out.print(" — ");
        out.print(origin.fallbackInfo());
      }
      if (origin.exception().isPresent()) {
        out.print(" ");
        out.print(origin.exception().get());
      }
      out.println();

      origin.descends().ifPresent(o -> printOriginLine(o, indent));
    }

    void reportRemoved(String caption, int count) {
      out.print("\u21aa ");
      out.print(caption);
      out.print(" (");
      out.print("-");
      out.print(count);
      out.print(")");
      out.println();
    }
  }

  private String formatProvides(Jaxrs.EndpointEntry entry) {
    return Errors.format("%s", entry.reference()) + " \u21e5 " + entry.target();
  }

  private String formatRequires(Jaxrs.EndpointEntry entry) {
    return Errors.format("%s", entry.reference()) + " \u21e4 " + entry.target();
  }

  private String formatEntries(Map.Entry<String, ?> entry) {
    return String.format("%s: %s", entry.getKey(), entry.getValue());
  }

  // we use entries so that we react (fact change) on the state change
  private String formatServices(Map.Entry<State, Service> byState) {
    var state = byState.getKey();
    var service = byState.getValue();
    String name = service.toString();
    if (name.contains("[")) {
      name = name.substring(0, name.indexOf('[')).trim();
    }
    return "[" + Strings.padEnd(state.name() + "]", 11, ' ') + " " + name;
  }

  private String formatDatabase(DatabaseModule.DatabaseForServicelet database) {
    return String.format("%s [%s] %s %s",
        database.database(), database.setup().mode(), database.setup().normalizedConnect(),
        ImmutableMap.of(
            "autostart", database.setup().autostart(),
            "template", database.setup().database()));
  }
}
