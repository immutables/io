package io.immutables.micro.wiring;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import com.google.common.base.Throwables;

/**
 * For the purposes of data access, remote endpoint access etc, we use proxy to setup isolation, dynamic resource
 * allocation or dynamic wiring. Abstract proxy provides typical handling of some object methods and exception
 * handling.
 */
abstract class DelegatingProxyHandler implements InvocationHandler {
  final Class<?> interfaceType;

  DelegatingProxyHandler(Class<?> interfaceType) {
    this.interfaceType = interfaceType;
  }

  /**
   * Override {@code delegate()} to resolve instance to delegate to, whether it's set during construction or dynamically
   * resolved.
   */
  abstract Object delegate();

  /** Used as suffix added to interfaceName for toString(). */
  String label() {
    return "";
  }

  @Override
  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // no one should actually call hashCode / equals (aside using
    // accidentally storing repos in a set, for example), maybe toString for logs/in debug.
    // but we implement these for a "quality of implementation" reasons
    if (isToString(method)) return toString();
    if (isHashCode(method)) return proxy.hashCode(); // basically regular identity hashCode
    if (isEquals(method)) return proxy == args[0]; // reference equality only for proxies

    return invokeDelegate(method, args);
  }

  /**
   * Override {@code invokeDelegate} to wrap call and then call {@code super.invokeDelegate} for default delegation
   * routine.
   */
  Object invokeDelegate(Method method, Object[] args) throws Exception {
    try {
      return method.invoke(delegate(), args);
    } catch (InvocationTargetException ex) {
      Throwables.propagateIfPossible(ex.getCause(), Exception.class);
      throw ex;
    }
  }

  private boolean isToString(Method method) {
    return method.getName().equals("toString")
        && method.getParameterCount() == 0;
  }

  private boolean isHashCode(Method method) {
    return method.getName().equals("hashCode")
        && method.getParameterCount() == 0;
  }

  private boolean isEquals(Method method) {
    return method.getName().equals("equals")
        && method.getParameterCount() == 1
        && method.getParameterTypes()[0] == Object.class;
  }

  final Object newProxy() {
    return Proxy.newProxyInstance(interfaceType.getClassLoader(),
        new Class<?>[]{interfaceType}, this);
  }

  @Override
  public String toString() {
    return interfaceType.getSimpleName() + label();
  }
}
