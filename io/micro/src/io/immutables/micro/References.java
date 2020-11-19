package io.immutables.micro;

import io.immutables.micro.Manifest.Reference;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Key;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Converts type and optional annotation to a reference value.
 *
 * <pre>
 * rawTypeName;qualifier=annotationRef;attr1=value1;attr2=value2
 * // references examples
 * super.duper.Cucumber
 * super.duper.Cucumber;qualifier=info
 * super.duper.Cucumber;qualifier=info;color=green;size=2
 * </pre>
 * <p>
 * <p>
 * Where 'annotationRef' is a short ref of annotation type name. All refs should be registered in META-INF/reference
 * file. If there's no qualifier annotation there will not be 'qualifier' part (;qualifier=). If there are no annotation
 * attributes there will not be "attrName=attrValue" pairs delimited by ";". Please note that 'rawTypeName' is used for
 * classloading. Type 'rawTypeName' is restricted to be top level class to avoid having '$' from binary name ({@code
 * package.Outer$Inner'}) in references.
 * <p>
 * The format should work on major filesystem, at least on Linux/Unix POSIX filesystems and NTFS.
 * <p>
 */
public class References {
  @VisibleForTesting static final BiMap<String, String> refToType = HashBiMap.create(refToType());

  private References() {
  }

  public static Reference reference(Class<?> type) {
    return reference(type, null, null);
  }

  public static Reference reference(Class<?> type, @Nullable Class<? extends Annotation> annotationType) {
    return reference(type, annotationType, null);
  }

  public static Reference reference(Class<?> type, Annotation annotation) {
    return reference(type, annotation.annotationType(), getAttributes(annotation));
  }

  public static Reference reference(Key<?> key) {
    Class<?> type = key.getTypeLiteral().getRawType();
    Annotation annotation = key.getAnnotation();
    return annotation == null ? reference(type, key.getAnnotationType()) : reference(type, annotation);
  }

  private static Reference reference(
      Class<?> type, @Nullable Class<? extends Annotation> annotationType,
      @Nullable Map<String, Object> attributes) {
    return Reference.of(getName(type) + getQualifierName(annotationType) + getKeyValue(attributes));
  }

  public static <T> Key<T> key(Reference reference) {
    if (!reference.value().contains(";")) {
      return Key.get(getRawType(reference));
    }

    List<String> parts = stream(reference.value().split(";")).collect(toList());
    parts.remove(0);//remove type name part

    String annotationReference = parts.get(0).replace("qualifier=", "");
    String annotationTypeName = refToType.get(annotationReference);
    if (annotationTypeName == null) {
      throw new IllegalArgumentException("Unregistered type reference: " + annotationReference);
    }
    Class<Annotation> annotationType = loadType(annotationTypeName);
    parts.remove(0);//remove annotation type name part

    if (parts.size() == 0) {
      return Key.get(getRawType(reference), annotationType);
    }

    return Key.get(getRawType(reference), annotation(annotationType, parts, annotationReference));
  }

  private static Annotation annotation(
      Class<? extends Annotation> annotationType,
      List<String> parts,
      String annotationReference) {

    Map<String, String> values = parts.stream()
        .collect(toMap(
            p -> p.split("=")[0],
            p -> p.split("=")[1]));

    return new AnnotationFactory<>(annotationType).create(values, () -> annotationReference);
  }

  private static <T> Class<T> getRawType(Reference reference) {
    return loadType(getTypeName(reference));
  }

  private static String getTypeName(Reference r) {
    String reference = r.value();
    return reference.contains(";") ? reference.substring(0, reference.indexOf(";")) : reference;
  }

  private static String getQualifierName(@Nullable Class<? extends Annotation> annotationType) {
    if (annotationType == null) return "";
    String ref = refToType.inverse().get(annotationType.getName());
    if (ref == null)
      throw new IllegalArgumentException("Please register reference for type: " + annotationType.getName());
    return ";qualifier=" + ref;
  }

  private static String getKeyValue(@Nullable Map<String, Object> m) {
    return m == null || m.isEmpty() ? "" : ";" + m.entrySet().stream()
        .sorted(comparingByKey())
        .map(Object::toString)//key=value
        .collect(joining(";"));
  }

  private static Map<String, Object> getAttributes(Annotation annotation) {
    return stream(annotation.annotationType().getDeclaredMethods())
        .collect(toMap(
            Method::getName,
            m -> getAttributeValue(m, annotation)));
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

  private static String getName(Class<?> type) {
    if (type.isAnonymousClass()
        || type.isLocalClass()
        || type.isSynthetic()
        || type.getCanonicalName() == null) {
      throw new IllegalArgumentException("Local or anonymous classes are not allowed");
    }
    return type.getName();
  }

  private static Map<String, String> refToType() {
    // TODO encapsulate into io.immutables.LoadedKeys
    // TODO load from all resources, not only from
    var splitter = Splitter.on(CharMatcher.anyOf("\n\r"))
        .omitEmptyStrings()
        .trimResults()
        .withKeyValueSeparator("=");
    URL resource = Resources.getResource(References.class, "/META-INF/qualifiers");
    try {
      return splitter.split(Resources.toString(resource, UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> loadType(String name) {
    // we need to be stringent not allowing arbitrary classes to load
    // the current check is a nominal, and non comprehensive, but
    // these lines exist as reminder to properly constrain class loading from JSON
    if (SANDBOX_CLASS_LOADING && ALLOWED_PREFIXES_FOR_CLASSES.stream().noneMatch(name::startsWith)) {
      throw new SecurityException(String.format("Not allowed to load type: %s. Only allowed classes below packages %s",
          name, ALLOWED_PREFIXES_FOR_CLASSES));
    }

    try {
      // Generics not supported for now, maybe will be at some point,
      // so we will happily fail on any declaration containing "<" ">" and
      // other symbols not used for raw fully qualified names
      return (Class<T>) Class.forName(name, false, References.class.getClassLoader());
    } catch (ClassNotFoundException ex) {
      throw new RuntimeException("Class `" + name + "` is not available", ex);
    }
  }

  private static final boolean SANDBOX_CLASS_LOADING = Boolean.getBoolean("references.sandbox-class-loading");

  private static final List<String> ALLOWED_PREFIXES_FOR_CLASSES = ImmutableList.of(
      "javax.inject.", "javax.annotation.", "java.lang.", "com.google.inject.name", "javax.sql");
}
