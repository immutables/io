package io.immutables.micro.wiring.jersey;

import java.io.IOException;
import java.util.function.Supplier;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.ext.Provider;

@Provider
public class AuthorizeClientFilter implements ClientRequestFilter {
  private final Supplier<String> authorizationSupplier;

  public AuthorizeClientFilter(Supplier<String> authorizationSupplier) {
    this.authorizationSupplier = authorizationSupplier;
  }

  @Override
  public void filter(ClientRequestContext context) throws IOException {
    var authorization = authorizationSupplier.get();
    if (!authorization.isEmpty()) {
      context.getHeaders().putSingle("Authorization", authorization);
    }
  }
}
