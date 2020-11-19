package io.immutables.micro.wiring;

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.ProvidesIntoOptional;
import com.google.inject.multibindings.ProvidesIntoOptional.Type;
import com.google.inject.multibindings.ProvidesIntoSet;
import java.time.Instant;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Jaxrs;
import io.immutables.micro.Launcher;
import io.immutables.micro.Manifest;
import io.immutables.micro.References;
import io.immutables.micro.Servicelet;
import static io.immutables.that.Assert.that;

public class JaxrsTest {
  static final Key<SampleEndpoint> KEY_SAMPLE_ENDPOINT = Key.get(SampleEndpoint.class);
  static final SampleEndpoint.Val VAL = SampleEndpoint.Val.of("A", 2, Instant.EPOCH);

  static class SampleResource implements SampleEndpoint {
    @Override public Val getVal() {
      return VAL;
    }

    @Inject @Context UriInfo uriInfo;

    @Override public String getBal() {
      return uriInfo.getPath();
    }
  }

  static final Servicelet.Name s1 = Servicelet.name("s1");
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
            .addBinding(s1)
            .toInstance(new Manifest.Builder()
                .name(s1)
                .addResources(new Manifest.Resource.Builder()
                    .reference(References.reference(KEY_SAMPLE_ENDPOINT))
                    .kind(Manifest.Kind.HTTP_PROVIDE)
                    .build())
                .build());
      })
      .addServicelet(new AbstractModule() {
        @ProvidesIntoSet
        public @Jaxrs.Registered Object sample() {
          return new SampleResource();
        }

        @ProvidesIntoOptional(Type.ACTUAL)
        public Servicelet.Name id() {
          return s1;
        }
      })
      .inject();

  static final ServiceManager manager = injector.getInstance(ServiceManager.class);
  static final WebTarget target = injector.getInstance(Jaxrs.WebTargeter.class)
      .target(KEY_SAMPLE_ENDPOINT);

  @BeforeClass
  public static void start() {
    manager.startAsync().awaitHealthy();
  }

  @AfterClass
  public static void stop() {
    manager.stopAsync().awaitStopped();
  }

  @Test
  public void resourceServed() {
    SampleEndpoint.Val response200 = target.path("/x")
        .request()
        .accept(MediaType.APPLICATION_JSON)
        .get(SampleEndpoint.Val.class);

    that(response200).equalTo(VAL);
  }

  @Test
  public void contextInfo() {
    String response200 = target.path("/x/y")
        .request()
        .accept(MediaType.TEXT_PLAIN)
        .get(String.class);

    that(response200).is("x/y");
  }

  @Test
  public void pathNotFound() {
    Response response404 = target.path("/wrong-path")
        .request()
        .accept(MediaType.TEXT_PLAIN)
        .get();

    that(response404.getStatusInfo().toEnum())
        .equalTo(Status.NOT_FOUND);
  }
}
