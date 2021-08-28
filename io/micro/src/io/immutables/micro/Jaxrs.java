package io.immutables.micro;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;
import com.google.common.net.HostAndPort;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import org.immutables.data.Data;
import org.immutables.value.Value;

/**
 * Umbrella type declaration to hold interfaces/annotations/factories for providers of JAX-RS http endpoints.
 */
@Data
@Value.Enclosing
public final class Jaxrs {
  private Jaxrs() {}

  /**
   * If applied to a set of Object, contributions to be registered as jaxrs resources and providers. Can also be used
   * for other kinds of Jaxrs specific resources and registrations.
   */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  public @interface Registered {}

  public static Multibinder<Object> registered(Binder binder) {
    return Multibinder.newSetBinder(binder, Object.class, Jaxrs.Registered.class);
  }

  /**
   * Used to bind client proxy.
   */
  public static <T> Provider<? extends T> proxyProvider(Key<T> key) {
    return new Provider<>() {
      @Inject EndpointProxyProvider provider;

      @Override
      public T get() {
        return provider.get(key);
      }
    };
  }

  /**
   * Provides interface implementations dynamically based on the key
   */
  public interface EndpointProxyProvider {
    <T> T get(Key<T> key);
  }

  public interface EndpointProxyCreator {
    Object create(EndpointEntry endpoint);
  }

  public interface WebTargeter {
    WebTarget target(Key<?> endpoint);

    default WebTarget target(Class<?> endpoint) {
      return target(Key.get(endpoint));
    }
  }

  /**
   * Entry which is used to map endpoint by key (type + annotation)
   */
  @Value.Immutable
  public interface EndpointEntry extends Origin.Traced<EndpointEntry> {
    @Value.Parameter
    Manifest.Reference reference();

    @Value.Parameter
    URI target();

    /**
     * Lazily converts to a key. Conversion from reference to key should only be attempted if interface classes and
     * annotation types available on the classpath so classloading will not fail. Otherwise don't touch it and just use
     * references in opaque way. For example discovery and registry components should only use Reference
     */
    @Value.Lazy
    default Key<?> key() {
      return References.key(reference());
    }

    static EndpointEntry of(Manifest.Reference reference, URI target) {
      return ImmutableJaxrs.EndpointEntry.of(reference, target);
    }

    class Builder extends ImmutableJaxrs.EndpointEntry.Builder {}
  }

  /**
   * Supplies URL for key (EndpointEntry) as asked by EndpointCatalog. Can resolve dynamically and entries can come and
   * go.
   */
  public interface EndpointResolver {
    /**
     * Can return endpoint entry for the key if matching endpoint becomes available.
     */
    Optional<EndpointEntry> get(Manifest.Reference reference);

    /**
     * List all available endpoints the supplier can find. If only lookup (by reference) is available, then empty set
     * can be returned
     */
    default Collection<EndpointEntry> scan() { return Set.of(); }
  }

  @Value.Immutable
  public interface Cors {
    Set<String> allowedMethods();
    Set<String> allowedHeaders();
    Set<String> allowedOrigins();
    @Value.Default
    default boolean allowCredentials() {
      return false;
    }
    @Value.Default
    default String exposedHeaders() {
      return "";
    }
    @Value.Default
    default boolean forceAnyOrigin() {
      return false;
    }
    @Value.Default
    default int corsMaxAge() {
      return -1;
    }

    class Builder extends ImmutableJaxrs.Cors.Builder {}
  }

  @Value.Immutable
  public interface Setup extends Origin.Traced<Setup> {
    /**
     * Host/and port on which to launch. Absent port will result in server starting on a standard port (defined by the
     * implementing module). Explicit zero port (":0") results in auto-assigning some arbitrary chosen free port.
     */
    @Value.Default
    default HostAndPort listen() {
      return HostAndPort.fromHost("localhost");
    }

    /**
     * Defines explicit bindings for endpoints. These takes highest priority while resolving required endpoints, i.e.
     * when creating local proxies to remote endpoints. Other ways to resolve includes {@link #resolveInPlatform()} and
     * {@link #resolveInDirectory()}.
     */
    Set<EndpointEntry> endpoints();

    /**
     * If {@code true}, the local endpoints launched on this same platform will be auto-resolved.
     */
    @Value.Default
    default boolean resolveInPlatform() {
      return false;
    }

    /**
     * Directory, which if specified, would be used to write endpoint entries and discover them from. If it's empty,
     * this means no publishing and no resolution will be attempted using the directory.
     */
    @Value.Default
    default String resolveInDirectory() {
      return "";
    }

    /**
     * If platform should try to discover and maintain a collection (separate from actually used).
     */
    @Value.Default
    default boolean discover() {
      return false;
    }

    @Value.Default
    default Duration refreshEndpointsAfter() {
      return Duration.ofSeconds(3);
    }

    @Value.Default
    default String fallbackUri() {
      return String.format("http://%s:%s/%s", FALLBACK_HOST, FALLBACK_PORT, FALLBACK_KEY);
    }

    default URI createFallbackUri(HostAndPort localplatform, Manifest.Reference reference) {
      var ref = reference.value();
      var key = References.key(reference);
      Class<? super Object> type = key.getTypeLiteral().getRawType();
      var path = type.getAnnotation(Path.class);
      var pname = type.getPackage().getName();
      if (path != null && (pname.equals("robofs") || pname.equals("edge"))) {
        ref = pname;
        //ref = ref.replace("//", "/");
      }
      return URI.create(fallbackUri()
          .replace(FALLBACK_HOST, localplatform.getHost())
          .replace(FALLBACK_PORT, localplatform.getPortOrDefault(80) + "")
          .replace(FALLBACK_KEY, ref));
    }

    /** By default our CORS is very permissive. */
    @Value.Default
    default Cors cors() {
      return new Cors.Builder()
          .allowCredentials(true)
          .addAllowedOrigins("*")
          .addAllowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
          .addAllowedHeaders("Origin", "Content-Type", "Accept", "Authorization")
          .build();
    }

    @Value.Default
    default String authorize() {
      return "";
    }

    String FALLBACK_HOST = "<localplatform>";
    String FALLBACK_PORT = "<port>";
    String FALLBACK_KEY = "<reference>";

    class Builder extends ImmutableJaxrs.Setup.Builder {}
  }
}
