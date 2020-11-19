package io.immutables.micro.tester;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/hype")
public interface HypeOfMoment {
  @GET
  String get();
}
