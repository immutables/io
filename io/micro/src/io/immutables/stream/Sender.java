package io.immutables.stream;

import java.util.Collections;

public interface Sender<R> {
  /** Topic of this publish handle. */
  Topic topic();

  // TODO asynchronous vs synchronous send, return Future
  void write(Iterable<R> records);

  default void write(R record) {
    write(Collections.singleton(record));
  }
}
