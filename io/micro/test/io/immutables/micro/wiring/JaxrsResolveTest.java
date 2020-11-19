package io.immutables.micro.wiring;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.micro.CodecsFactory;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Jaxrs;
import io.immutables.micro.References;
import io.immutables.that.Assert;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.junit.AfterClass;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class JaxrsResolveTest {
  private final OkJson json = OkJson.configure(c -> c.add(new CodecsFactory()));
  private final Codec<Jaxrs.EndpointEntry> entryCodec = json.get(Jaxrs.EndpointEntry.class);
  private final Key<String> keyString = Key.get(String.class);
  private final Key<Integer> keyInteger = Key.get(Integer.class);
  private final Key<Void> keyVoid = Key.get(Void.class);
  private final Jaxrs.EndpointEntry entryString = Jaxrs.EndpointEntry.of(References.reference(keyString), URI.create("ns://ks1"));
  private final Jaxrs.EndpointEntry entryString2 = Jaxrs.EndpointEntry.of(References.reference(keyString), URI.create("ns://ks2"));
  private final Jaxrs.EndpointEntry entryInteger = Jaxrs.EndpointEntry.of(References.reference(keyInteger), URI.create("ns://ki2"));
  private final Jaxrs.EndpointEntry entryVoid = Jaxrs.EndpointEntry.of(References.reference(keyVoid), URI.create("ns://kv1"));
  private final Map<Key<?>, Jaxrs.EndpointEntry> inPlatform = ImmutableMap.of(keyString, entryString, keyInteger, entryInteger);

  private final HostAndPort hostPort = HostAndPort.fromString("defaultHost");

  private static File dir = Files.createTempDir();

  @AfterClass
  public static void cleanup() {
    Files.fileTraverser().depthFirstPostOrder(dir).forEach(File::delete);
  }

  @Test(expected = IllegalArgumentException.class)
  public void noCanonicalName() {
    class Local {}
    References.reference(Key.get(Local.class)).value();
    that().unreachable();
  }

  @Test
  public void toFilename() {
    Assert.that(References.reference(Key.get(Void.class)).value()).is("java.lang.Void");
    Assert.that(References.reference(Key.get(Object.class, Named.class)).value())
        .is("java.lang.Object;qualifier=named");
    Assert.that(References.reference(Key.get(String.class, Names.named("a"))).value())
        .is("java.lang.String;qualifier=named;value=a");
  }

  @Test
  public void onlyConfiguredAndFallbackEntries() {
    Jaxrs.Setup setup = new Jaxrs.Setup.Builder()
        .addEndpoints(entryString2)
        .build();

    DirectoryEndpoints service = new DirectoryEndpoints(
        setup,
        json,
        ExceptionSink.assertNoUnhandled(),
        inPlatform.values());

    EndpointCatalog catalog = new EndpointCatalog(
        setup,
        inPlatform,
        hostPort,
        Sets.newHashSet(service));

    service.startAsync().awaitRunning();

    that(catalog.get(keyString)).equalTo(entryString2);
    that(catalog.get(keyInteger).target()).equalTo(URI.create("http://defaultHost:80/java.lang.Integer"));
    that(catalog.get(keyVoid).target()).equalTo(URI.create("http://defaultHost:80/java.lang.Void"));

    service.stopAsync().awaitTerminated();
  }

  @Test
  public void onlyConfiguredAndInPlatformEntries() {
    Jaxrs.Setup setup = new Jaxrs.Setup.Builder()
        .addEndpoints(entryString2)
        .resolveInPlatform(true)
        .build();

    DirectoryEndpoints service = new DirectoryEndpoints(
        setup,
        json,
        ExceptionSink.assertNoUnhandled(),
        inPlatform.values());

    EndpointCatalog catalog = new EndpointCatalog(
        setup,
        inPlatform,
        hostPort,
        Sets.newHashSet(service));

    service.startAsync().awaitRunning();

    that(catalog.get(keyString)).equalTo(entryString2);
    that(catalog.get(keyInteger)).equalTo(entryInteger);

    service.stopAsync().awaitTerminated();
  }

  @Test
  public void storeInPlatformEntries() {
    Jaxrs.Setup setup = new Jaxrs.Setup.Builder()
        .resolveInDirectory(dir.getPath())
        .build();

    DirectoryEndpoints service = new DirectoryEndpoints(
        setup,
        json,
        ExceptionSink.assertNoUnhandled(),
        inPlatform.values());

    EndpointCatalog catalog = new EndpointCatalog(
        setup,
        inPlatform,
        hostPort,
        Set.of(service));

    service.startAsync().awaitRunning();

    // file written
    File file = new File(dir, References.reference(keyString).value());
    that(file).is(File::exists);
    // don't resolve in platform so we will get it via file
    that(catalog.get(keyString)).equalTo(entryString);
    that(file.delete()).is(true);
    // delete file and we will not resolve it any more
    that(catalog.get(keyString).target()).equalTo(URI.create("http://defaultHost:80/java.lang.String"));

    service.stopAsync().awaitTerminated();
  }

  @Test
  public void fullPriorityResolution() throws IOException {
    Jaxrs.Setup setup = new Jaxrs.Setup.Builder()
        .addEndpoints(entryString2)
        .resolveInDirectory(dir.getPath())
        .resolveInPlatform(true)
        .build();

    DirectoryEndpoints service = new DirectoryEndpoints(
        setup,
        json,
        ExceptionSink.assertNoUnhandled(),
        inPlatform.values());

    EndpointCatalog catalog = new EndpointCatalog(
        setup,
        inPlatform,
        hostPort,
        Sets.newHashSet(service));

    service.startAsync().awaitRunning();

    // file written
    File file = new File(dir, References.reference(keyVoid).value());
    Files.asCharSink(file, StandardCharsets.UTF_8).write(json.toJson(entryVoid, entryCodec));

    // from setup
    that(catalog.get(keyString)).equalTo(entryString2);
    // from in-platform
    that(catalog.get(keyInteger)).equalTo(entryInteger);
    // from file
    that(catalog.get(keyVoid)).equalTo(entryVoid);
    // fallback
    that(catalog.get(Key.get(Double.class)).target()).equalTo(URI.create("http://defaultHost:80/java.lang.Double"));

    service.stopAsync().awaitTerminated();
  }

  @Test
  public void shouldFallbackOnManySuppliers() throws IOException {
    Jaxrs.Setup setup = new Jaxrs.Setup.Builder()
        .addEndpoints(entryString2)
        .resolveInDirectory(dir.getPath())
        .resolveInPlatform(true)
        .build();

    DirectoryEndpoints service = new DirectoryEndpoints(
        setup,
        json,
        ExceptionSink.assertNoUnhandled(),
        inPlatform.values());

    EndpointCatalog catalog = new EndpointCatalog(
        setup,
        inPlatform,
        hostPort,
        Sets.newHashSet(service, t -> Optional.of(Jaxrs.EndpointEntry.of(References.reference(keyVoid), URI.create("ns://anoOther1")))));

    service.startAsync().awaitRunning();

    // file written
    File file = new File(dir, References.reference(keyVoid).value());
    Files.asCharSink(file, StandardCharsets.UTF_8).write(json.toJson(entryVoid, entryCodec));

    Jaxrs.EndpointEntry result = catalog.get(keyVoid);
    that(result.target()).equalTo(URI.create("http://defaultHost:80/java.lang.Void"));
    that(result.origin().fallbackInfo()).startsWith("Conflicting endpoint targets for");

    service.stopAsync().awaitTerminated();
  }

  @Test
  public void shouldReturnFromTheSupplier() throws IOException {
    Jaxrs.Setup setup = new Jaxrs.Setup.Builder()
        .addEndpoints(entryString2)
        .resolveInDirectory(dir.getPath())
        .resolveInPlatform(true)
        .build();

    DirectoryEndpoints service = new DirectoryEndpoints(
        setup,
        json,
        ExceptionSink.assertNoUnhandled(),
        inPlatform.values());

    Map<Key<?>, Jaxrs.EndpointEntry> entries = Lists.newArrayList(
        Jaxrs.EndpointEntry.of(
            References.reference(Key.get(JaxrsResolveTest.class, Names.named("var1"))),
            URI.create("ns://byJaxrsResolveTest1")),
        Jaxrs.EndpointEntry.of(
            References.reference(Key.get(String.class, Names.named("var2"))),
            URI.create("ns://byString2")))
        .stream()
        .collect(Collectors.toMap(Jaxrs.EndpointEntry::key, o -> o));

    Jaxrs.EndpointResolver supplier =
        reference -> Optional.of(entries.get(References.key(reference)));

    EndpointCatalog catalog = new EndpointCatalog(
        setup,
        inPlatform,
        hostPort,
        Sets.newHashSet(service, supplier));

    service.startAsync().awaitRunning();

    // file written
    File file = new File(dir, References.reference(keyVoid).value());
    Files.asCharSink(file, StandardCharsets.UTF_8).write(json.toJson(entryVoid, entryCodec));

    Jaxrs.EndpointEntry result = catalog.get(Key.get(String.class, Names.named("var2")));
    that(result.target()).equalTo(URI.create("ns://byString2"));
    that(result.origin().fallbackInfo()).isEmpty();

    that(catalog.get(Key.get(String.class)).target()).equalTo(URI.create("ns://ks2"));

    service.stopAsync().awaitTerminated();
  }
}
