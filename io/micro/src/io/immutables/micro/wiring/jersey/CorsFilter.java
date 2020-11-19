package io.immutables.micro.wiring.jersey;

import io.immutables.micro.Jaxrs;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import com.google.common.base.Joiner;
import com.google.common.net.HttpHeaders;

/**
 * Closely resembles filter in RESTEasy implementation. Can be configured in JAX-RS setup config.
 * @see Jaxrs.Setup#cors()
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {
  private final Jaxrs.Cors cors;

  public CorsFilter(Jaxrs.Cors cors) {
    this.cors = cors;
  }

  @Override
  public void filter(ContainerRequestContext request) throws IOException {
    @Nullable String origin = request.getHeaderString(HttpHeaders.ORIGIN);
    if (origin == null) return; // if no origin - this is not a browser interested in CORS

    // Origin check
    if (!cors.allowedOrigins().contains(ANY) && !cors.allowedOrigins().contains(origin)) {
      request.setProperty(SKIP_RESPONSE_HANDLING, true);
      throw new ForbiddenException("Not allowed origin: " + origin);
    }

    // Preflight OPTIONS handling
    if (request.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS)) {
      Response.ResponseBuilder r = Response.ok();

      r.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
      r.header(HttpHeaders.VARY, HttpHeaders.ORIGIN);

      if (cors.allowCredentials()) {
        r.header(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, String.valueOf(true));
      }

      @Nullable String requestMethods = request.getHeaderString(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);

      if (requestMethods != null && !cors.allowedMethods().isEmpty()) {
        r.header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, join(cors.allowedMethods()));
      }

      @Nullable String allowHeaders = request.getHeaderString(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);

      if (allowHeaders != null && !cors.allowedHeaders().isEmpty()) {
        r.header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, join(cors.allowedHeaders()));
      }

      if (cors.corsMaxAge() > -1) {
        r.header(HttpHeaders.ACCESS_CONTROL_MAX_AGE, cors.corsMaxAge());
      }

      request.abortWith(r.build());
    }
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    @Nullable String origin = request.getHeaderString(HttpHeaders.ORIGIN);

    if (origin == null
        || request.getProperty(SKIP_RESPONSE_HANDLING) == Boolean.TRUE
        || request.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS)) {
      // not relevant or handled by other means
      return;
    }

    var h = response.getHeaders();
    h.putSingle(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, cors.allowedOrigins().contains(ANY) ? ANY : origin);
    h.putSingle(HttpHeaders.VARY, HttpHeaders.ORIGIN);

    if (cors.allowCredentials()) {
      h.putSingle(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, String.valueOf(true));
    }

    if (!cors.exposedHeaders().isEmpty()) {
      h.putSingle(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, cors.exposedHeaders());
    }
  }

  private static String join(Iterable<String> strings) {
    return Joiner.on(", ").join(strings);
  }

  public static final String SKIP_RESPONSE_HANDLING = "cors.skip-response-handling";
  public static final String ANY = "*";
}
