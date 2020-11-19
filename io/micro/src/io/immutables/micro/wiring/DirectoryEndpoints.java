package io.immutables.micro.wiring;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Jaxrs;
import io.immutables.micro.Manifest;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.google.common.util.concurrent.AbstractScheduledService;

/**
 * Mini-service to store/load endpoint entries from setup entries, from in-platform launched entries or special
 * directory.
 */
public final class DirectoryEndpoints extends AbstractScheduledService implements Jaxrs.EndpointResolver {
  private final Collection<Jaxrs.EndpointEntry> inPlatform;
  private final Optional<JsonEntriesDirectory<Manifest.Reference, Jaxrs.EndpointEntry>> directory;
  private final Codec<Jaxrs.EndpointEntry> codec;

  DirectoryEndpoints(
      Jaxrs.Setup setup,
      OkJson json,
      ExceptionSink exceptions,
      Collection<Jaxrs.EndpointEntry> inPlatform) {
    this.inPlatform = inPlatform;
    this.codec = json.get(Jaxrs.EndpointEntry.class);
    this.directory = !setup.resolveInDirectory().isEmpty()
        ? Optional.of(new JsonEntriesDirectory<>(
        json,
        codec,
        exceptions,
        setup.origin(),
        setup.resolveInDirectory(),
        Jaxrs.EndpointEntry::reference))
        : Optional.empty();
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(5, 5, TimeUnit.SECONDS);
  }

  @Override
  protected void startUp() {
    publishInPlatformEntries(true);
  }

  @Override
  protected void runOneIteration() {
    publishInPlatformEntries(false);
  }

  private void publishInPlatformEntries(boolean force) {
    directory.ifPresent(d -> {
      for (Jaxrs.EndpointEntry e : inPlatform) {
        d.store(e, !force);
      }
    });
  }

  @Override
  public Collection<Jaxrs.EndpointEntry> scan() {
    return directory
        .map(JsonEntriesDirectory::scan)
        .orElse(inPlatform);
  }

  @Override
  public Optional<Jaxrs.EndpointEntry> get(Manifest.Reference reference) {
    return directory.flatMap(e -> e.load(reference));
  }
}
