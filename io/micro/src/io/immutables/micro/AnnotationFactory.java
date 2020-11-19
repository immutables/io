package io.immutables.micro;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.primitives.Primitives;

final class AnnotationFactory<A extends Annotation> {
  private final Class<A> annotationType;
  private final ImmutableMap<String, Method> attributes;

  AnnotationFactory(Class<A> annotationType) {
    assert annotationType.isAnnotation();
    this.annotationType = annotationType;
    this.attributes = Maps.uniqueIndex(Arrays.asList(annotationType.getDeclaredMethods()), Method::getName);
  }

  A create(Map<String, ?> values, Supplier<String> path) {
    return instantiate(validatedAttributes(values, path));
  }

  @SuppressWarnings("unchecked") // proxy is instance of A annotation type, runtime reflection
  private A instantiate(ImmutableMap<String, Object> values) {
    Class<?>[] interfaces = new Class<?>[]{annotationType, Annotation.class};
    return (A) Proxy.newProxyInstance(annotationType.getClassLoader(), interfaces,
        values.isEmpty()
            ? new NoAttributeHandler(annotationType)
            : new WithAttributesHandler(annotationType, attributes, values));
  }

  private ImmutableMap<String, Object> validatedAttributes(Map<String, ?> values, Supplier<String> path) {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    List<String> missing = ImmutableList.of();
    for (Map.Entry<String, Method> e : attributes.entrySet()) {
      String name = e.getKey();
      Method accessor = e.getValue();
      @Nullable Object value = values.get(name);
      if (value == null) {
        value = accessor.getDefaultValue();
      }
      if (value != null) {
        Class<?> type = accessor.getReturnType();
        if (!Primitives.wrap(type).isInstance(value)) {
          try {
            value = convert(value.toString(), type);
          } catch (RuntimeException ex) {
            throw new RuntimeException(
                String.format("Cannot read %s at %s, failed parse attribute %s: %s << %s",
                    annotationType.getName(), path.get(), name, type, value), ex);
          }
        }
        builder.put(name, value);
        values.remove(name);
      } else {
        // replace empty immutable list, easier than messing with nulls
        if (missing.isEmpty()) missing = new ArrayList<>();
        missing.add(name);
      }
    }
    // as we remove any found attribute, `values` map can contain remaining unknow
    // attributes which we will report
    if (!values.isEmpty() || !missing.isEmpty()) {
      throw new RuntimeException(
          String.format("Cannot read %s at %s, missing attributes: %s, unknown: %s",
              annotationType.getName(), path.get(), missing, values.keySet()));
    }
    return builder.build();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object convert(String value, Class<?> type) {
    if (type.isEnum()) return Enum.valueOf((Class) type, value);
    if (String.class == type) return value;
    if (boolean.class == type) return Boolean.parseBoolean(value);
    if (byte.class == type) return Byte.parseByte(value);
    if (short.class == type) return Short.parseShort(value);
    if (int.class == type) return Integer.parseInt(value);
    if (long.class == type) return Long.parseLong(value);
    if (float.class == type) return Float.parseFloat(value);
    if (double.class == type) return Double.parseDouble(value);
    throw new AssertionError("Unsupported type for annotation attribute");
  }

  private static final class NoAttributeHandler implements InvocationHandler, Annotation {
    final Class<? extends Annotation> annotationType;

    NoAttributeHandler(Class<? extends Annotation> annotationType) {
      this.annotationType = annotationType;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return method.invoke(this, args);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return annotationType;
    }

    @Override
    public int hashCode() {
      return 0; // no attributes => 0 (zero, per specification)
    }

    @Override
    public boolean equals(Object obj) {
      return annotationType.isInstance(obj);
    }
  }

  /**
   * This class would be fully independent, just shares immutables attributes instance with other instances of the same
   * annotation types, no references to type adapter or type adapter factory is held.
   */
  private static final class WithAttributesHandler implements InvocationHandler, Annotation {
    private final int hashCode;
    private final Class<? extends Annotation> annotationType;
    private final ImmutableMap<String, Method> attributes;
    private final ImmutableMap<String, Object> values;

    WithAttributesHandler(
        Class<? extends Annotation> annotationType,
        ImmutableMap<String, Method> attributes,
        ImmutableMap<String, Object> values) {
      this.annotationType = annotationType;
      this.attributes = attributes;
      this.values = values;
      this.hashCode = annotationHashCode(values);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (attributes.containsKey(method.getName())) {
        return values.get(method.getName());
      }
      return method.invoke(this, args);
    }

    private int annotationHashCode(ImmutableMap<String, Object> values) {
      return values.entrySet()
          .stream()
          .mapToInt(e -> attributeHashCode(e.getKey(), e.getValue()))
          .sum();
    }

    private int attributeHashCode(String name, Object value) {
      // it's important to check all primitive array types, we use Arrays.deepHashCode
      // on a single element wrapping arrays, then we correct value subtracting initial seed
      int valueHashCode = value.getClass().isArray()
          ? Arrays.deepHashCode(new Object[]{value}) - 31
          : value.hashCode();
      // also there should not be null (annotation attributes cannot have null), don't handle it

      // this is per Annotation specification
      return 127 * name.hashCode() ^ valueHashCode;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return annotationType;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) return false;
      if (obj == this) return true;
      if (!annotationType.isInstance(obj)) return false;
      for (Map.Entry<String, Method> e : attributes.entrySet()) {
        Object value = values.get(e.getKey());
        Object oppositeValue = getAttributeValue(e.getValue(), obj);
        if (!value.equals(oppositeValue)) return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "@" + annotationType.getCanonicalName() + (!values.isEmpty() ? values : "");
    }
  }

  private static Object getAttributeValue(Method accessor, Object annotation) {
    try {
      return accessor.invoke(annotation);
    } catch (IllegalAccessException ex) {
      throw new AssertionError(ex);
    } catch (InvocationTargetException ex) {
      Throwables.throwIfUnchecked(ex.getCause());
      throw new RuntimeException(ex.getCause());
    }
  }
}
