package io.immutables.micro.wiring;

import java.time.Instant;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.immutables.data.Data;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Enclosing
@Data
@Path("/x")
@Produces(MediaType.APPLICATION_JSON)
public interface SampleEndpoint {
  @GET
  Val getVal();

  @Path("/y")
  @Produces(MediaType.TEXT_PLAIN)
  @GET
  String getBal();

  @Immutable
  interface Val {
    @Parameter String a();
    @Parameter int b();
    @Parameter Instant at();

    static Val of(String a, int b, Instant at) {
      return ImmutableSampleEndpoint.Val.of(a, b, at);
    }
  }
}
