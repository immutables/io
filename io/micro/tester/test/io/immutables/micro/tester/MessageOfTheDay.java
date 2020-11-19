package io.immutables.micro.tester;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/motd")
public interface MessageOfTheDay {
  @GET
  String get();
}
