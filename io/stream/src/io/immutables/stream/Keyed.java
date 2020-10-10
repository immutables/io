package io.immutables.stream;

/**
 * If record (message) type implements this interface, then each such record will be inserted into
 * the stream using this key.
 * @param <K> the type of key, must be serializable (using stream's codes technology)
 */
public interface Keyed<K> {
  K key();
}
