package sample;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("x")
public interface End {
  @GET
  String get();

  @GET
  @Path("/y/{param}")
  @Produces(MediaType.APPLICATION_JSON)
  List<Inl> get(@PathParam("param") Inl in);
}
