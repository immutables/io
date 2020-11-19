package io.immutables.micro.wiring;

import io.immutables.micro.Jaxrs;
import io.immutables.micro.Manifest;
import io.immutables.micro.Origin;
import io.immutables.micro.References;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.google.inject.Key;

/**
 * To store endpoint entries from setup entries, from in-platfrom launched entries or provided dynamically by suppliers
 * One default supplier should always exists in the set - {@link DirectoryEndpoints}
 */
final class EndpointCatalog {
  private final Jaxrs.Setup setup;
  private final HostAndPort hostPort;
  private final Map<Key<?>, Jaxrs.EndpointEntry> inPlatform;
  private final ImmutableMap<Key<?>, Jaxrs.EndpointEntry> setupEndpoints;
  private final Set<Jaxrs.EndpointResolver> dynamicSuppliers;

  EndpointCatalog(
      Jaxrs.Setup setup,
      Map<Key<?>, Jaxrs.EndpointEntry> inPlatform,
      HostAndPort hostPort,
      Set<Jaxrs.EndpointResolver> dynamicSuppliers) {
    this.setup = setup;
    this.inPlatform = inPlatform;
    this.hostPort = hostPort;
    this.setupEndpoints = Maps.uniqueIndex(setup.endpoints(), Jaxrs.EndpointEntry::key);
    this.dynamicSuppliers = dynamicSuppliers;
  }

  Jaxrs.EndpointEntry get(Key<?> key) {
    if (setupEndpoints.containsKey(key)) {
      return setupEndpoints.get(key);
    }

    if (setup.resolveInPlatform() && inPlatform.containsKey(key)) {
      return inPlatform.get(key);
    }

    var reference = References.reference(key);
    List<Jaxrs.EndpointEntry> entries = dynamicSuppliers
        .stream()
        .flatMap(provider -> provider.get(reference).stream())
        .collect(Collectors.toList());

    if (entries.size() == 1) return entries.get(0);
    if (entries.isEmpty()) return fallbackEntry(reference);
    return duplicateEntry(reference);
  }

  private Jaxrs.EndpointEntry duplicateEntry(Manifest.Reference reference) {
    URI fallbackUri = setup.createFallbackUri(hostPort, reference);

    return Jaxrs.EndpointEntry.of(reference, fallbackUri).withOrigin(new Origin.Builder()
        .resourceFromStackTrace()
        .notAvailable(false)
        .fallbackInfo("Conflicting endpoint targets for: " + reference)
        .isFallback(true)
        .build());
  }

  private Jaxrs.EndpointEntry fallbackEntry(Manifest.Reference reference) {
    URI fallbackUri = setup.createFallbackUri(hostPort, reference);

    return Jaxrs.EndpointEntry.of(reference, fallbackUri)
        .withOrigin(new Origin.Builder()
            .descends(setup.origin())
            .resourceFromStackTrace()
            .notAvailable(false)
            .fallbackInfo("fallback from setup")
            .isFallback(true)
            .build());
  }
}
