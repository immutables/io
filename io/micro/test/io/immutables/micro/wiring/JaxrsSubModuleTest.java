package io.immutables.micro.wiring;


import io.immutables.micro.*;


import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.ProvidesIntoOptional;
import com.google.inject.multibindings.ProvidesIntoOptional.Type;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class JaxrsSubModuleTest {

  @Path("/x")
  @Produces(MediaType.TEXT_PLAIN)
  public static class SampleResource {
    @Inject Injector injector;
    @Inject Servicelet.Name name;
    @Path("/y")
    public Subresource get() {
      Subresource sub = new Subresource();
      injector.injectMembers(sub);
      return sub;
    }
  }

  public static class Subresource {
    @Inject DataData data;

    @GET public String get() { return data.get(); }
  }

  static public class DataData {
    public String get() {
      return "Hello!";
    }
  }

  static final Servicelet.Name interceptName = Servicelet.name("intercept");
  static final Injector injector = new Launcher()
      .add(new ServiceletNameModule())
      .add(new ServiceManagerModule())
      .add(new JsonModule())
      .add(new JaxrsModule())
      .add(new JaxrsRemoteModule())
      .add(b -> {
        b.bind(Jaxrs.Setup.class).toInstance(
            new Jaxrs.Setup.Builder()
                .listen(HostAndPort.fromParts("localhost", 0))
                .resolveInPlatform(true)
                .build());
        b.bind(ExceptionSink.class).toInstance(ExceptionSink.assertNoUnhandled());
        MapBinder.newMapBinder(b, Servicelet.Name.class, Manifest.class)
            .addBinding(interceptName)
            .toInstance(new Manifest.Builder()
                .name(interceptName)
                .addResources(new Manifest.Resource.Builder()
                    .reference(References.reference(SampleResource.class))
                    .kind(Manifest.Kind.HTTP_PROVIDE)
                    .build())
                .build());
      })
      .addServicelet(new AbstractModule() {
        @Provides
        public DataData data() { return new DataData(); }

        @ProvidesIntoSet
        public @Jaxrs.Registered Object sample() {
          return new SampleResource();
        }

        @ProvidesIntoOptional(Type.ACTUAL)
        public Servicelet.Name id() {
          return interceptName;
        }
      })
      .inject();

  static final ServiceManager manager = injector.getInstance(ServiceManager.class);
  static final WebTarget target = injector.getInstance(Jaxrs.WebTargeter.class)
      .target(SampleResource.class);

  @BeforeClass
  public static void start() {
    manager.startAsync().awaitHealthy();
  }

  @AfterClass
  public static void stop() {
    manager.stopAsync().awaitStopped();
  }

  @Test
  public void interceptedWithInjectedData() {
    String response = target.path("/x/y")
        .request()
        .get(String.class);

    that(response).is("Hello!");
  }
}
