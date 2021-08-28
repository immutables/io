package io.immutables.micro.wiring;

import io.immutables.codec.OkJaxrsMessageBodyProvider;
import io.immutables.codec.OkJson;
import io.immutables.micro.*;
import io.immutables.micro.wiring.jersey.BridgeInjections;
import io.immutables.micro.wiring.jersey.AuthorizeClientFilter;
import io.immutables.micro.wiring.jersey.CorsFilter;
import io.immutables.micro.wiring.jersey.ParameterConverter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ParamConverterProvider;
import com.google.common.net.HostAndPort;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainerProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.spi.ExtendedExceptionMapper;

public final class JaxrsModule implements Module {

  @Override
  public void configure(Binder binder) {
    Jaxrs.registered(binder);
  }

  @ProvidesIntoSet
  public MixinModule registeredDeclaration() {
    return Jaxrs::registered;
  }

  /**
   * Calculate actual host port, for local
   */
  @Provides
  @Singleton
  public @Jaxrs.Registered HostAndPort actual(Jaxrs.Setup setup) {
    HostAndPort listen = setup.listen();
    if (!listen.hasPort()) {
      return listen.withDefaultPort(PORT_STANDARD);
    }
    if (LocalPorts.isLocalhost(listen.getHost()) && listen.getPort() == PORT_AUTO_ASSIGN) {
      return HostAndPort.fromParts(listen.getHost(), LocalPorts.findSomeFreePort());
    }
    return listen;
  }

  @ProvidesIntoSet
  public @Jaxrs.Registered Object jsonBodyProvider(OkJson json) {
    return new OkJaxrsMessageBodyProvider(json);
  }

  @ProvidesIntoSet
  public @Jaxrs.Registered Object corsFilter(Jaxrs.Setup setup) {
    return new CorsFilter(setup.cors());
  }

  @ProvidesIntoSet
  public @Jaxrs.Registered Object authorizeFilter(Jaxrs.Setup setup) {
    return new AuthorizeClientFilter(setup::authorize);
  }

  @ProvidesIntoSet
  public @Jaxrs.Registered Object parameterConverter(ParamConverterProvider converter) {
    return converter;
  }

  @Provides
  @Singleton
  public ParamConverterProvider parameterConverter(OkJson okJson) {
    return new ParameterConverter(okJson);
  }

  @Provides
  @Singleton
  public Client client(@Jaxrs.Registered Set<Object> registrations) {
    ClientConfig config = new ClientConfig()
        .property(ClientProperties.CONNECT_TIMEOUT, 5_000)
        .property(ClientProperties.READ_TIMEOUT, 10_000);
    registrations.forEach(config::register);
    return ClientBuilder.newClient(config);
  }

  @Provides
  public @Systems.Shared Client sharedClient(Client client) {
    return client;
  }

  @ProvidesIntoSet
  public @Jaxrs.Registered Object fallbackExceptionHandler(ExceptionSink sink) {
    return new ExtendedExceptionMapper<Throwable>() {
      @Override
      public boolean isMappable(Throwable exception) {
        return !(exception instanceof WebApplicationException);
      }

      @Override
      public Response toResponse(Throwable exception) {
        ExceptionToken token = sink.unhandled(exception);
        return Response.serverError()
            .type(MediaType.APPLICATION_JSON)
            .entity(new GenericEntity<>(
                Map.of("error", "Unexpected server error", "token", token.getValue()),
                new TypeToken<Map<String, String>>() {}.getType()
            ))
            .build();
      }
    };
  }

  @ProvidesIntoSet
  public Service http(
      @Jaxrs.Registered HostAndPort listen,
      @Jaxrs.Registered Set<Object> registrations,
      @Systems Provider<Set<Injector>> servicelets,
      ExceptionSink exceptions) {
    // servicelets provider should not be dereferenced up until service start!
    return new HttpService(listen, registrations, servicelets, exceptions);
  }

  static final class HttpService extends AbstractIdleService {
    private HttpServer server;

    private final Set<Object> registrations;
    private final Provider<Set<Injector>> modulesProvider;
    private final HostAndPort listen;
    private final ExceptionSink exceptions;

    public HttpService(
        HostAndPort listen,
        Set<Object> registrations,
        Provider<Set<Injector>> modulesProvider,
        ExceptionSink exceptions) {
      this.registrations = registrations;
      this.modulesProvider = modulesProvider;
      this.listen = listen;
      this.exceptions = exceptions;
    }

    @Override
    protected void startUp() throws Exception {
      server = new HttpServer();
      server.addListener(createNetworkListener());
      ServerConfiguration configuration = server.getServerConfiguration();
      // also mimic here what's GrizzlyHttpServerFactory doing
      configuration.setPassTraceRequest(true);
      configuration.setDefaultQueryEncoding(StandardCharsets.UTF_8);

      installHandlers(configuration);
      server.start();
    }

    @Override
    protected void shutDown() {
      server.shutdown();
    }

    private NetworkListener createNetworkListener() {
      NetworkListener listener = new NetworkListener("http", listen.getHost(), listen.getPort());
      // this mimicking the same handling as in GrizzlyHttpServerFactory, but can be changed later
      listener.getTransport()
          .getWorkerThreadPoolConfig()
          .setThreadFactory(new ThreadFactoryBuilder()
              .setNameFormat("http-%d")
              .setUncaughtExceptionHandler(ExceptionSink.asUncaughtHandler(exceptions))
              .build());

      return listener;
    }

    private void installHandlers(ServerConfiguration configuration) {
      for (Injector module : modulesProvider.get()) {
        Servicelet.Name name = ServiceletNameModule.getName(module);
        HttpHandler container = createHttpHandler(module);
        configuration.addHttpHandler(container, "/" + name);
      }
    }

    // ContainerProvider does not allow to populate parent injector manager on creating container
    // Should call specific package-private constructor for that
    private HttpHandler createHttpHandler(Injector module) {
      ResourceConfig application = resourceConfig(module);
      BridgeInjections.injector.set(module);

      GrizzlyHttpContainer container = new GrizzlyHttpContainerProvider()
          .createContainer(GrizzlyHttpContainer.class, application);

      BridgeInjections.injector.remove();
      return container;
    }

    private ResourceConfig resourceConfig(Injector module) {
      // so far resources give warning (of not being "providers" when registered this way)
      // currently I don't know any way to register resource instances without issuing the warning
      return new ResourceConfig()
          .setApplicationName(ServiceletNameModule.getName(module).toString())
          .registerInstances(registrations)
          .registerInstances(module.getInstance(REGISTERED))
          .property(ServerProperties.WADL_FEATURE_DISABLE, true)
          .property(ServerProperties.RESOURCE_VALIDATION_DISABLE, true)
          .property(ServerProperties.BV_FEATURE_DISABLE, true);
    }
  }

  private static final Key<Set<Object>> REGISTERED = Keys.setOf(Object.class, Jaxrs.Registered.class);
  private static final int PORT_AUTO_ASSIGN = 0;
  private static final int PORT_STANDARD = 9900;
}
