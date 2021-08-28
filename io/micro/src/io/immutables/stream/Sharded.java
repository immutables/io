package io.immutables.stream;

/**
 * In order to be intelligently split between shards/partitions (to benefit from locality and
 * partial ordering for subsets of data), each message can provide shard key, which is then
 * used by partitioner (within producer) to decide specific partition on which this record will
 * end up.
 * @param <P> the type of shard key object
 */
public interface Sharded<P> {
  P shardKey();
}
