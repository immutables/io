package io.immutables.micro.wiring.jersey;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.security.AccessController;
import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import org.glassfish.jersey.internal.util.ReflectionHelper;

// FIXME This should not be improved/refactored. It has to be changed into using code generator for
// some modern technology

/**
 * Factory for client-side representation of a resource. See the <a href="package-summary.html">package overview</a> for
 * an example on how to use this class.
 * @author Martin Matula
 */
public final class WebResourceFactory implements InvocationHandler {

  private static final String[] EMPTY = {};

  private final ParamConverterProvider converters;
  private final WebTarget target;
  private final MultivaluedMap<String, Object> headers;
  private final List<Cookie> cookies;
  private final Form form;

  private static final MultivaluedMap<String, Object> EMPTY_HEADERS = new MultivaluedHashMap<>();
  private static final Form EMPTY_FORM = new Form();
  private static final List<Class<?>> PARAM_ANNOTATION_CLASSES = List.of(PathParam.class, QueryParam.class,
      HeaderParam.class, CookieParam.class, MatrixParam.class, FormParam.class);

  /**
   * Creates a new client-side representation of a resource described by the interface passed in the first argument.
   * <p/>
   * Calling this method has the same effect as calling {@code WebResourceFactory.newResource(resourceInterface,
   * rootTarget, false)}.
   * @param <C> Type of the resource to be created.
   * @param resourceInterface Interface describing the resource to be created.
   * @param target WebTarget pointing to the resource or the parent of the resource.
   * @return Instance of a class implementing the resource interface that can be used for making requests to the server.
   */
  public static <C> C newResource(final Class<C> resourceInterface, final WebTarget target,
      ParamConverterProvider paramConverterProvider) {
    return newResource(resourceInterface, target, false, EMPTY_HEADERS, Collections.<Cookie>emptyList(), EMPTY_FORM,
        paramConverterProvider);
  }

  /**
   * Creates a new client-side representation of a resource described by the interface passed in the first argument.
   * @param <C> Type of the resource to be created.
   * @param resourceInterface Interface describing the resource to be created.
   * @param target WebTarget pointing to the resource or the parent of the resource.
   * @param ignoreResourcePath If set to true, ignores path annotation on the resource interface (this is used when
   *     creating sub-resources)
   * @param headers Header params collected from parent resources (used when creating a sub-resource)
   * @param cookies Cookie params collected from parent resources (used when creating a sub-resource)
   * @param form Form params collected from parent resources (used when creating a sub-resource)
   * @return Instance of a class implementing the resource interface that can be used for making requests to the server.
   */
  @SuppressWarnings("unchecked")
  public static <C> C newResource(
      final Class<C> resourceInterface,
      final WebTarget target,
      final boolean ignoreResourcePath,
      final MultivaluedMap<String, Object> headers,
      final List<Cookie> cookies,
      final Form form,
      ParamConverterProvider paramConverterProvider) {
    return (C) Proxy.newProxyInstance(
        AccessController.doPrivileged(ReflectionHelper.getClassLoaderPA(resourceInterface)),
        new Class[]{resourceInterface},
        new WebResourceFactory(
            paramConverterProvider,
            ignoreResourcePath ? target : addPathFromAnnotation(resourceInterface, target),
            headers,
            cookies,
            form));
  }

  private WebResourceFactory(
      ParamConverterProvider converters,
      final WebTarget target,
      final MultivaluedMap<String, Object> headers,
      final List<Cookie> cookies,
      final Form form) {
    this.converters = converters;
    this.target = target;
    this.headers = headers;
    this.cookies = cookies;
    this.form = form;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
    if (args == null && method.getName().equals("toString")) {
      return toString();
    }

    if (args == null && method.getName().equals("hashCode")) {
      // unique instance in the JVM, and no need to override
      return hashCode();
    }

    if (args != null && args.length == 1 && method.getName().equals("equals")) {
      // unique instance in the JVM, and no need to override
      return equals(args[0]);
    }

    // get the interface describing the resource
    final Class<?> proxyIfc = proxy.getClass().getInterfaces()[0];

    // response type
    final Class<?> responseType = method.getReturnType();

    // determine method name
    String httpMethod = getHttpMethodName(method);
    if (httpMethod == null) {
      for (final Annotation ann : method.getAnnotations()) {
        httpMethod = getHttpMethodName(ann.annotationType());
        if (httpMethod != null) {
          break;
        }
      }
    }

    // create a new UriBuilder appending the @Path attached to the method
    WebTarget newTarget = addPathFromAnnotation(method, target);

    if (httpMethod == null) {
      if (newTarget == target) {
        // no path annotation on the method -> fail
        throw new UnsupportedOperationException("Not a resource method.");
      } else if (!responseType.isInterface()) {
        // the method is a subresource locator, but returns class,
        // not interface - can't help here
        throw new UnsupportedOperationException("Return type not an interface");
      }
    }

    // process method params (build maps of (Path|Form|Cookie|Matrix|Header..)Params
    // and extract entity type
    final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<String, Object>(this.headers);
    final LinkedList<Cookie> cookies = new LinkedList<>(this.cookies);
    final Form form = new Form();
    form.asMap().putAll(this.form.asMap());
    final Annotation[][] paramAnns = method.getParameterAnnotations();
    Type[] parameterTypes = method.getGenericParameterTypes();
    Class<?>[] rawTypes = method.getParameterTypes();

    Object entity = null;
    Type entityType = null;
    for (int i = 0; i < paramAnns.length; i++) {
      final Map<Class<?>, Annotation> anns = new HashMap<>();
      for (final Annotation ann : paramAnns[i]) {
        anns.put(ann.annotationType(), ann);
      }
      Annotation ann;
      Object value = args[i];
      if (!hasAnyParamAnnotation(anns)) {
        entityType = method.getGenericParameterTypes()[i];
        entity = value;
      } else {
        if (value == null && (ann = anns.get(DefaultValue.class)) != null) {
          value = ((DefaultValue) ann).value();
        } else if (value != null) {

          ParamConverter<Object> converter = null;
          Class<?> rt = rawTypes[i];

          if (rt != String.class && !rt.isPrimitive() && !Primitives.isWrapperType(rt)) {
            if (rt.isArray()) {
              int len = Array.getLength(value);
              Object[] ov = new Object[len];
              rt = rt.getComponentType();
              if (rt != String.class && !rt.isPrimitive() && !Primitives.isWrapperType(rt)) {
                var conv = (ParamConverter<Object>) converters.getConverter(rt, rt, paramAnns[i]);
                for (int j = 0; j < ov.length; j++) {
                  ov[j] = conv.toString(Array.get(value, j));
                }
              } else {
                for (int j = 0; j < ov.length; j++) {
                  ov[j] = String.valueOf(Array.get(value, j));
                }
              }
              value = ov;
            } else if (Collection.class.isAssignableFrom(rt)) {
              Type t = parameterTypes[i];
              Object[] ov = ((Collection<?>) value).toArray();
              if (t instanceof ParameterizedType) {
                Type argument = ((ParameterizedType) t).getActualTypeArguments()[0];
                rt = TypeToken.of(argument).getRawType();
                var conv = (ParamConverter<Object>) converters.getConverter(rt, argument, paramAnns[i]);
                for (int j = 0; j < ov.length; j++) {
                  ov[j] = conv.toString(ov[j]);
                }
              } else {
                for (int j = 0; j < ov.length; j++) {
                  ov[j] = String.valueOf(ov[j]);
                }
              }
              value = ov;
            } else {
              var conv = (ParamConverter<Object>) converters.getConverter(rt, parameterTypes[i], paramAnns[i]);
              value = conv.toString(value);
            }
          }
        }

        if (value != null) {
          if ((ann = anns.get(PathParam.class)) != null) {
            newTarget = newTarget.resolveTemplate(((PathParam) ann).value(), value);
          } else if ((ann = anns.get((QueryParam.class))) != null) {
            if (value instanceof Object[]) {
              newTarget = newTarget.queryParam(((QueryParam) ann).value(), (Object[]) value);
            } else {
              newTarget = newTarget.queryParam(((QueryParam) ann).value(), value);
            }
          } else if ((ann = anns.get((HeaderParam.class))) != null) {
            if (value instanceof Object[]) {
              headers.addAll(((HeaderParam) ann).value(), (Object[]) value);
            } else {
              headers.addAll(((HeaderParam) ann).value(), value);
            }
          } else if ((ann = anns.get((CookieParam.class))) != null) {
            final String name = ((CookieParam) ann).value();
            Cookie c;
            if (value instanceof Object[]) {
              for (final Object v : (Object[]) value) {
                if (!(v instanceof Cookie)) {
                  c = new Cookie(name, v.toString());
                } else {
                  c = (Cookie) v;
                  if (!name.equals(((Cookie) v).getName())) {
                    // is this the right thing to do? or should I fail? or ignore the difference?
                    c = new Cookie(name, c.getValue(), c.getPath(), c.getDomain(), c.getVersion());
                  }
                }
                cookies.add(c);
              }
            } else {
              if (!(value instanceof Cookie)) {
                cookies.add(new Cookie(name, value.toString()));
              } else {
                c = (Cookie) value;
                if (!name.equals(((Cookie) value).getName())) {
                  // is this the right thing to do? or should I fail? or ignore the difference?
                  cookies.add(new Cookie(name, c.getValue(), c.getPath(), c.getDomain(), c.getVersion()));
                }
              }
            }
          } else if ((ann = anns.get((MatrixParam.class))) != null) {
            if (value instanceof Object[]) {
              newTarget = newTarget.matrixParam(((MatrixParam) ann).value(), (Object[]) value);
            } else {
              newTarget = newTarget.matrixParam(((MatrixParam) ann).value(), value);
            }
          } else if ((ann = anns.get((FormParam.class))) != null) {
            if (value instanceof Object[]) {
              for (final Object v : ((Object[]) value)) { form.param(((FormParam) ann).value(), String.valueOf(v)); }
            } else {
              form.param(((FormParam) ann).value(), String.valueOf(value));
            }
          }
        }
      }
    }

    if (httpMethod == null) {
      // the method is a subresource locator
      return WebResourceFactory.newResource(responseType, newTarget, true, headers, cookies, form, converters);
    }

    // accepted media types
    Produces produces = method.getAnnotation(Produces.class);
    if (produces == null) {
      produces = proxyIfc.getAnnotation(Produces.class);
    }
    final String[] accepts = (produces == null) ? EMPTY : produces.value();

    // determine content type
    String contentType = null;
    if (entity != null) {
      final List<Object> contentTypeEntries = headers.get(HttpHeaders.CONTENT_TYPE);
      if ((contentTypeEntries != null) && (!contentTypeEntries.isEmpty())) {
        contentType = contentTypeEntries.get(0).toString();
      } else {
        Consumes consumes = method.getAnnotation(Consumes.class);
        if (consumes == null) {
          consumes = proxyIfc.getAnnotation(Consumes.class);
        }
        if (consumes != null && consumes.value().length > 0) {
          contentType = consumes.value()[0];
        }
      }
    }

    Invocation.Builder builder = newTarget.request()
        .headers(headers) // this resets all headers so do this first
        .accept(accepts); // if @Produces is defined, propagate values into Accept header; empty
    // array is NO-OP

    for (final Cookie c : cookies) {
      builder = builder.cookie(c);
    }

    final Object result;

    if (entity == null && !form.asMap().isEmpty()) {
      entity = form;
      contentType = MediaType.APPLICATION_FORM_URLENCODED;
    } else {
      if (contentType == null) {
        contentType = MediaType.APPLICATION_OCTET_STREAM;
      }
      if (!form.asMap().isEmpty()) {
        if (entity instanceof Form) {
          ((Form) entity).asMap().putAll(form.asMap());
        } else {
          // TODO: should at least log some warning here
        }
      }
    }

    final GenericType responseGenericType = new GenericType(method.getGenericReturnType());
    if (entity != null) {
      if (entityType instanceof ParameterizedType) {
        entity = new GenericEntity(entity, entityType);
      }
      result = builder.method(httpMethod, Entity.entity(entity, contentType), responseGenericType);
    } else {
      result = builder.method(httpMethod, responseGenericType);
    }

    return result;
  }

  private boolean hasAnyParamAnnotation(final Map<Class<?>, Annotation> anns) {
    for (final Class<?> paramAnnotationClass : PARAM_ANNOTATION_CLASSES) {
      if (anns.containsKey(paramAnnotationClass)) {
        return true;
      }
    }
    return false;
  }

  private Object[] convert(final Collection<?> value) {
    return value.toArray();
  }

  private static WebTarget addPathFromAnnotation(final AnnotatedElement ae, WebTarget target) {
    final Path p = ae.getAnnotation(Path.class);
    if (p != null) {
      target = target.path(p.value());
    }
    return target;
  }

  @Override
  public String toString() {
    return target.toString();
  }

  private static String getHttpMethodName(final AnnotatedElement ae) {
    final HttpMethod a = ae.getAnnotation(HttpMethod.class);
    return a == null ? null : a.value();
  }
}
