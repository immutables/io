package io.immutables.micro.tester;

import io.immutables.that.Assert;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;
import static io.immutables.that.Assert.that;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BlockingRecordBufferTest {

  @Test
  public void getSingleRecord() {
    BlockingRecordBuffer<String> buffer = new BlockingRecordBuffer<>(1, SECONDS);
    buffer.queue.add("record_0");

    List<String> actualRecords = buffer.take(1);

    that(actualRecords).hasSize(1);
    that(actualRecords.get(0)).is("record_0");
  }

  @Test
  public void getTenRecords() {
    List<Integer> expectedRecords = IntStream.range(0, 10).boxed().collect(Collectors.toList());
    BlockingRecordBuffer<Integer> buffer = new BlockingRecordBuffer<>(1, SECONDS);
    buffer.queue.addAll(expectedRecords);

    List<Integer> actualRecords = buffer.take(10);

    that(actualRecords).hasSize(10);
    that(actualRecords).isOf(expectedRecords);
  }

  @Test
  public void takeUntilStopWord() {
    List<String> expectedRecords = List.of("record_0", "record_1");
    BlockingRecordBuffer<String> buffer = new BlockingRecordBuffer<>(1, SECONDS);

    buffer.queue.addAll(expectedRecords);
    buffer.queue.add("stop");
    buffer.queue.add("shouldn't be collected 1");
    buffer.queue.add("shouldn't be collected 2");

    List<String> actualRecords = buffer.takeUntil("stop"::equals);

    that(actualRecords).hasSize(2);
    that(actualRecords).isOf(expectedRecords);
  }

  @Test
  public void collectFirstFiveEvenNumbers() {
    BlockingRecordBuffer<Integer> buffer = new BlockingRecordBuffer<>(1, SECONDS);
    IntStream.range(0, 100).forEach(buffer.queue::add);

    List<Integer> actualRecords = buffer
        .filter(value -> value % 2 == 0)
        .takeWhile(records -> records.size() < 5);

    List<Integer> expectedRecords = List.of(0, 2, 4, 6, 8);
    that(actualRecords).hasSize(5);
    that(actualRecords).isOf(expectedRecords);
  }

  @Test
  public void failCollectingRecordsDueToTimeout() {
    BlockingRecordBuffer<String> buffer = new BlockingRecordBuffer<>(1, MILLISECONDS);
    buffer.queue.addAll(List.of("record_0", "record_1"));

    that(() -> buffer.take(10)).thrown(AssertionError.class);
    that(buffer.records.size()).is(2);
  }

  @Test
  public void giveupCollectingRecordsDueToTimeout() {
    List<String> expectedRecords = List.of("record_0", "record_1");
    BlockingRecordBuffer<String> buffer = new BlockingRecordBuffer<>(10, MILLISECONDS);
    buffer.queue.addAll(expectedRecords);

    List<String> actualRecords = buffer
        .giveupAfter(2, MILLISECONDS)
        .take(10);

    that(actualRecords).hasSize(2);
    that(actualRecords).isOf(expectedRecords);
  }

  @Test
  public void resetBuffer() {
    BlockingRecordBuffer<String> buffer = new BlockingRecordBuffer<>(1, SECONDS);
    buffer.queue.addAll(List.of("record_0", "record_1"));

    buffer.reset();

    buffer.queue.add("record_2");
    List<String> actualRecords = buffer.take(1);
    that(actualRecords).hasSize(1);
    that(actualRecords.get(0)).is("record_2");
  }

  @Test
  public void failOnIllegalDefaultTimeout() {
    Assert.that(() -> new BlockingRecordBuffer<String>(0, SECONDS))
        .thrown(IllegalArgumentException.class)
        .hasToString("java.lang.IllegalArgumentException: Timeout has to be greater than 0");
  }

  @Test
  public void failOnIllegalTimeout() {
    BlockingRecordBuffer<String> buffer = new BlockingRecordBuffer<>(1, SECONDS);
    that(() -> buffer.timeoutAfter(-1, MILLISECONDS))
        .thrown(IllegalArgumentException.class)
        .hasToString("java.lang.IllegalArgumentException: Timeout has to be greater than 0");
  }

  @Test
  public void failOnIllegalGiveupTimeout() {
    BlockingRecordBuffer<String> buffer = new BlockingRecordBuffer<>(1, SECONDS);
    that(() -> buffer.giveupAfter(0, MILLISECONDS))
        .thrown(IllegalArgumentException.class)
        .hasToString("java.lang.IllegalArgumentException: Timeout has to be greater than 0");
  }
}
