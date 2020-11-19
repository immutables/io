package io.immutables.micro.wiring;

import io.immutables.codec.OkJson;
import io.immutables.micro.*;

import io.immutables.micro.spect.ManifestCatalog;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.immutables.data.Data;
import org.immutables.value.Value;

@Data
@Value.Enclosing
public class ManifestCatalogExport extends AbstractModule {

  @Value.Immutable
  public interface Setup extends Origin.Traced<Setup> {
    default @Value.Default String directory() {
      return "";
    }

    default boolean enabled() {
      return !directory().isEmpty();
    }

    class Builder extends ImmutableManifestCatalogExport.Setup.Builder {}
  }

  @Override protected void configure() {
    bind(ManifestExporter.class).in(Singleton.class);
  }

  public static class ManifestExporter extends AbstractIdleService {
    @Inject ExceptionSink exceptions;
    @Inject OkJson json;
    @Inject Setup setup;
    @Inject @Systems ConcurrentMap<Servicelet.Name, Injector> servicelets;
    @Nullable JsonEntriesDirectory<String, ManifestCatalog> directory;

    @Override protected void startUp() {
      if (setup.enabled()) {
        directory = new JsonEntriesDirectory<>(
            json, json.get(ManifestCatalog.class), exceptions, setup.origin(), setup.directory(), this::filename);

        var manifests = servicelets.values().stream()
            .map(inj -> inj.getInstance(Manifest.class))
            .collect(Collectors.toList());

        var catalog = ManifestCatalog.collectFrom(manifests);

        directory.store(catalog);
      }
    }

    private String filename(ManifestCatalog catalog) {
      return Integer.toHexString(catalog.hashCode());
    }

    public Collection<ManifestCatalog> scan() {
      return directory != null ? directory.scan() : Set.of();
    }

    @Override protected void shutDown() {}
  }

  @ProvidesIntoSet
  Service exporter(Setup setup, ManifestExporter exporter) {
    return setup.enabled() ? exporter : ServiceManagerModule.noop();
  }
}

