package io.immutables.stream;

/**
 * Delegate like interface which can be used to supply Key and/or Shard with message type that is not implementing
 * {@link Keyed} / {@link Sharded} interfaces.
 */
public interface Record<R> {
  R get();
  // TODO think how to propagate key type information (just runtime type?)
}
