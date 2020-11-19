package io.immutables.micro;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import com.google.inject.Key;
import com.google.inject.util.Types;

/**
 * Factories for keys which wraps generic types.
 */
public final class Keys {
  private Keys() {}

  @SuppressWarnings("unchecked") // safe by reliably composing Set<T> type information
  public static <T> Key<Set<T>> setOf(Class<? extends T> type, Class<? extends Annotation> annotation) {
    return (Key<Set<T>>) Key.get(Types.newParameterizedType(Set.class, type), annotation);
  }

  @SuppressWarnings("unchecked") // safe by reliably composing Set<T> type information
  public static <T> Key<Set<T>> setOf(Class<? extends T> type) {
    return (Key<Set<T>>) Key.get(Types.newParameterizedType(Set.class, type));
  }

  @SuppressWarnings("unchecked") // safe by reliably composing Map<K, V> type information
  public static <K, V> Key<Map<K, V>> mapOf(Class<? extends K> key, Class<? extends V> value) {
    return (Key<Map<K, V>>) Key.get(Types.newParameterizedType(Map.class, key, value));
  }
}
