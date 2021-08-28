package io.immutables.stream;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Receiver<R> {

  void on(Records<R> records);

  /**
   * The pack of records (microbatch) read from a single shard.
   *
   * @param <R> type of records
   */
  interface Records<R> extends Iterable<R> {
    /**
     * Topic on which the records was received.
     */
    Topic topic();

    /**
     * Specific shard from where these records have been read.
     */
    int shard();

    /**
     * Number of records in this microbatch.
     */
    int size();

    /**
     * Commits all the messages in this batch, usually this can be left out if auto-commit is
     * used, but regardless it allows to do synchronous commit from the handler, so if it fails
     * with exception, some measures can be taken right away.
     */
    void commit();

    /**
     * Streams the records.
     */
    default Stream<R> stream() {
      return StreamSupport.stream(Spliterators.spliterator(iterator(), size(),
          Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.SIZED), false);
    }
  }
}
