package io.immutables.micro.wiring;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import okio.Okio;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Origin;
import io.immutables.micro.Origin.Traced;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class JsonEntriesDirectory<K, V extends Traced<V>> {
  private final Path directory;
  private final OkJson json;
  private final Codec<V> codec;
  private final ExceptionSink exceptions;
  private final Origin origin;
  private final Function<V, K> extractKey;

  JsonEntriesDirectory(
      OkJson json,
      Codec<V> codec,
      ExceptionSink exceptions,
      Origin origin,
      String path,
      Function<V, K> extractKey) {
    this.json = json;
    this.origin = origin;
    this.exceptions = exceptions;
    this.directory = resolvePath(path);
    this.codec = codec;
    this.extractKey = extractKey;
  }

  protected String toFilename(K key) {
    return key.toString();
  }

  private boolean directoryExistsOrCreated() {
    if (!Files.isDirectory(directory)) {
      try {
        Files.createDirectories(directory);
      } catch (IOException ex) {
        exceptions.unhandled(ex);
        return false;
      }
    }
    return true;
  }

  public Optional<V> load(K key) {
    return loadFrom(directory.resolve(toFilename(key)));
  }

  public void store(V value) {
    store(value, false);
  }

  public void store(V value, boolean ifNotExists) {
    if (!directoryExistsOrCreated()) return;

    K key = extractKey.apply(value);

    String filename = toFilename(key);
    Path destination = directory.resolve(filename);

    if (ifNotExists && Files.exists(destination)) return;

    Path temporary = directory.resolve(".-" + filename + "-" + Integer.toHexString(this.hashCode()));
    destination.toFile().deleteOnExit();
    // this should be moved, but if not, we still will opportunistically request delete on exit
    temporary.toFile().deleteOnExit();
    try {
      try (var sink = Okio.buffer(Okio.sink(temporary, StandardOpenOption.CREATE_NEW))) {
        json.toJson(value, sink, codec);
      }
      // as soon as we have written the file we're atomically rename it
      // and scheduling it for deletion on exit, this is best effort cleanup, so we don't do
      // deletion on shutdown.
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      exceptions.unhandled(ex);
    }
  }

  public Collection<V> scan() {
    Set<V> entries = new HashSet<>();
    if (Files.isDirectory(directory)) {
      try (var stream = Files.list(directory)) {
        stream
            .filter(f -> !f.getFileName().toString().startsWith("."))
            .filter(Files::isReadable)
            .flatMap(f -> loadFrom(f).stream())
            .forEach(entries::add);
      } catch (Exception ex) {
        exceptions.unhandled(ex);
      }
    }
    return entries;
  }

  private Optional<V> loadFrom(Path file) {
    if (Files.isReadable(file)) try {
      try (var source = Okio.buffer(Okio.source(file))) {
        var entry = json.fromJson(source, codec)
            .withOrigin(new Origin.Builder()
                .descends(origin)
                .resource(file.toUri().toString())
                .build());

        return Optional.of(entry);
      }
    } catch (Exception ex) {
      exceptions.unhandled(ex);
    }
    return Optional.empty();
  }

  /**
   * Resolves absolute directory path
   */
  private static Path resolvePath(String path) {
    if (path.startsWith("~/")) {
      String home = System.getProperty("user.home", "");
      if (!home.isEmpty()) {
        path = path.replaceFirst("~", home);
      }
    }
    return Path.of(path).toAbsolutePath();
  }
}
