package io.immutables.micro.wiring;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/dynamic")
@Produces(MediaType.APPLICATION_JSON)
public interface DynamicSampleEndpoint {
  @GET
  SampleEndpoint.Val getVal();
}
