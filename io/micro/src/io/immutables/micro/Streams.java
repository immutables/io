package io.immutables.micro;

import io.immutables.micro.wiring.LocalPorts;
import io.immutables.stream.Receiver;
import io.immutables.stream.Sender;
import io.immutables.stream.Topic;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Service;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.util.Types;
import org.immutables.data.Data;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Parameter;

@Data
@Value.Enclosing
public class Streams {

  @Value.Immutable
  public interface Setup extends Origin.Traced<Setup> {
    /**
     * Host/and port on which to launch. Absent port will result in server starting on a standard port (defined by the
     * implementing module). Explicit zero port (":0") results in auto-assigning some arbitrary chosen free port.
     */
    @Default
    default HostAndPort hostPort() {
      return HostAndPort.fromHost("localhost");
    }

    /**
     * Should autostarting on localhost be attempted (by launching docker container).
     */
    @Default
    default boolean autostart() {
      return false;
    }

    @Value.Check
    default void check() {
      if (autostart()) {
        Preconditions.checkState(LocalPorts.isLocalhost(hostPort().getHost()), "auto start on localhost only, but got" +
            " " + hostPort().getHost());
      }
    }

    @Default
    default Streams.Setup.Mode topicMode() {
      return Streams.Setup.Mode.EXISTING;
    }

    @Default
    default int shards() {
      return 1;
    }

    enum Mode {
      EXISTING,
      CREATE_AND_DROP,
      CREATE_IF_NOT_EXIST
    }

    class Builder extends ImmutableStreams.Setup.Builder {}
  }

  public interface SenderFactory {
    <R> Sender<R> create(Key<R> key);
  }

  public interface DispatcherFactory {
    <R> Service create(Key<R> key, Optional<String> group, Provider<Receiver<R>> provider);
  }

  /**
   * Keys (even if qualified by annotation) are not very good
   */
  @Value.Immutable
  public interface TopicImport {
    @Parameter
    Key<?> key();

    static TopicImport of(Key<?> key) {
      return ImmutableStreams.TopicImport.of(key);
    }
  }

  public static <R> Provider<Sender<R>> senderProvider(Key<R> key) {
    return new Provider<>() {
      @Inject SenderFactory factory;

      @Override
      public Sender<R> get() {
        return factory.create(key);
      }
    };
  }

  public static <R> Provider<Service> dispatcherProvider(
      Key<R> key,
      Key<Receiver<R>> receiverKey,
      Optional<String> group) {
    return new Provider<>() {
      @Inject DispatcherFactory factory;
      @Inject Injector injector;

      @Override
      public Service get() {
        Provider<Receiver<R>> receiverProvider = injector.getProvider(receiverKey);
        return factory.create(key, group, receiverProvider);
      }
    };
  }

  @SuppressWarnings("unchecked") // safe by composing type reflectively
  public static <T> Key<Receiver<T>> toReceiverKey(Key<T> key, Annotation annotation) {
    return (Key<Receiver<T>>) Key.get(
        Types.newParameterizedType(Receiver.class, key.getTypeLiteral().getType()),
        annotation);
  }

  @SuppressWarnings("unchecked") // safe by composing type reflectively
  public static <T> Key<Receiver<T>> toReceiverKey(Key<T> key) {
    ParameterizedType type = Types.newParameterizedType(Receiver.class, key.getTypeLiteral().getType());
    return (Key<Receiver<T>>) key.ofType(type);
  }

  @SuppressWarnings("unchecked") // safe by composing type reflectively
  public static <T> Key<Sender<T>> toSenderKey(Key<T> key) {
    return (Key<Sender<T>>) key.ofType(
        Types.newParameterizedType(Sender.class, key.getTypeLiteral().getType()));
  }

  /**
   * We derive topic name from key, which is either comes from {@code @Named} annotation or by combining annotation and
   * record type name. Later we can write more configurable resolution, where topic exporter will decide how to map name
   * to key. Currently we don't have topic exporter, only users in form of producers and consumers, and actual topics
   * are auto-created for testing/non-production platform.
   */
  public static Topic topicFor(Key<?> key) {
    @Nullable Annotation annotation = key.getAnnotation();
    if (annotation instanceof Named) {
      return Topic.of(((Named) annotation).value());
    }
    if (annotation instanceof com.google.inject.name.Named) {
      return Topic.of(((com.google.inject.name.Named) annotation).value());
    }
    String qualifier;
    if (key.hasAttributes()) {
      qualifier = key.getAnnotation() + " ";
    } else if (key.getAnnotationType() != null) {
      qualifier = "@" + key.getAnnotationType().getCanonicalName() + " ";
    } else {
      qualifier = "";
    }
    return Topic.of(qualifier + key.getTypeLiteral().getRawType().getName());
  }

  public static boolean requiresStream(Collection<Manifest> manifests) {
    return manifests.stream()
        .flatMap(m -> m.resources().stream())
        .map(Manifest.Resource::kind)
        .anyMatch(k -> k == Manifest.Kind.STREAM_CONSUME || k == Manifest.Kind.STREAM_PRODUCE);
  }
}
