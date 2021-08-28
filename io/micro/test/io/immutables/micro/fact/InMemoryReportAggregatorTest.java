package io.immutables.micro.fact;

import io.immutables.codec.OkJson;
import io.immutables.micro.CodecsFactory;
import io.immutables.micro.ExceptionSink;
import org.junit.Test;
import java.time.Instant;
import java.util.Map;
import static io.immutables.micro.fact.ReportEntry.Kind.D;
import static io.immutables.micro.fact.ReportEntry.Kind.V;
import static io.immutables.that.Assert.that;

@SuppressWarnings("ConstantConditions")
public class InMemoryReportAggregatorTest {
  static final OkJson coder = OkJson.configure(c -> c.add(new CodecsFactory()));
  static final ExceptionSink sink = ExceptionSink.assertNoUnhandled();
  final InMemoryReportAggregator aggregator = new InMemoryReportAggregator(sink,coder);

  @Test(expected = AssertionError.class)
  public void cantUpdateRoot() {
    aggregator.report(ReportEntry.of(0, 0, V, "", "42", Instant.now()));
  }

  @Test
  public void canIntroduceAndDiscontinue() {
    aggregator.report(ReportEntry.of(1, 0, V, "", "Earth is not flat", Instant.now()));
    var output = aggregator.get(1);
    that(output).notNull();
    that(output.id()).is(1);

    aggregator.report(ReportEntry.of(1, 0, D, "", "oh no", Instant.now()));
    output = aggregator.get(1);
    that(output).isNull();
  }

  @Test
  public void canUpdate() {
    aggregator.report(ReportEntry.of(2, 0, V, "", "Gravity is force", Instant.now()));
    var output = aggregator.get(2);
    that(output).notNull();
    that(output.id()).is(2);

    aggregator.report(ReportEntry.of(2, 0, V, "", "Gravity is not force", Instant.now()));
    output = aggregator.get(2);
    that(output.id()).is(2);
    that(output.value()).hasToString("Gravity is not force");
  }

  @Test(expected = AssertionError.class)
  public void cantChangeParentOnUpdate() {
    aggregator.report(ReportEntry.of(0, 0, V, "", "42", Instant.now()));
    aggregator.report(ReportEntry.of(0, 0, V, "", "420", Instant.now()));
  }

  @Test
  public void canIntroduceChildrenAndDiscontinue() {
    aggregator.report(ReportEntry.of(3, 0, V, "", "Boolean algebra is awesome", Instant.now()));
    aggregator.report(ReportEntry.of(31, 3, V, "associativity", "a ∨ (b ∨ c) = (a ∨ b) ∨ c", Instant.now()));
    aggregator.report(ReportEntry.of(32, 3, V, "associativity", "a ∧ (b ∧ c) = (a ∧ b) ∧ c", Instant.now()));
    aggregator.report(ReportEntry.of(33, 3, V, "commutativity", "a ∨ b = b ∨ a", Instant.now()));
    aggregator.report(ReportEntry.of(34, 3, V, "absorption", "a ∨ (a ∧ b) = a", Instant.now()));
    aggregator.report(ReportEntry.of(35, 3, V, "identity", "a ∨ 0 = a", Instant.now()));
    aggregator.report(ReportEntry.of(36, 3, V, "identity", "a ∧ 1 = a", Instant.now()));
    aggregator.report(ReportEntry.of(37, 3, V, "distributivity", "a ∨ (b ∧ c) = (a ∨ b) ∧ (a ∨ c)", Instant.now()));
    aggregator.report(ReportEntry.of(38, 3, V, "complements", "a ∨ ¬a = 1", Instant.now()));
    aggregator.report(ReportEntry.of(39, 3, V, "complements", "a ∧ ¬a = 0", Instant.now()));

    var outputBoolean = aggregator.get(3);
    that(outputBoolean).notNull();
    that(outputBoolean.id()).is(3);
    that(outputBoolean.children()).hasSize(9);
    var outputAbsorption = aggregator.get(34);
    that(outputAbsorption).notNull();
    that(outputAbsorption.id()).is(34);

    aggregator.report(ReportEntry.of(33, 3, D, "commutativity", "a ∧ b = b ∧ a", Instant.now()));
    that(aggregator.get(33)).isNull();

    outputBoolean = aggregator.get(3);
    that(outputBoolean.children()).hasSize(8);

    aggregator.report(ReportEntry.of(3, 3, D, "", "2", Instant.now()));
    that(aggregator.get(3)).isNull();
  }

  @Test
  public void cantGetIfNotExist() {
    that(aggregator.get(1)).isNull();
  }

  @Test
  public void canIntroduceWithUnknownParent() {
    aggregator.report(ReportEntry.of(3, 1, V, "", "Boolean algebra is awesome", Instant.now()));
    that(aggregator.get(3)).notNull();
    that(aggregator.get(1)).notNull();
    that(aggregator.get(1).children().get(0).id()).is(3);
    that(aggregator.get(0).children().get(0).id()).is(1);

    aggregator.report(ReportEntry.of(1, 2, V, "", "parent", Instant.now()));
    that(aggregator.get(1)).notNull();
    that(aggregator.get(1).children().get(0).id()).is(3);
    that(aggregator.get(0).children().get(0).id()).is(2);
    that(aggregator.get(2)).notNull();
    that(aggregator.get(2).children().get(0).id()).is(1);
  }

  @Test
  public void test() {
    that(aggregator.asMap("")).equalTo("");
    that(aggregator.asMap("abc")).equalTo("abc");
    that(aggregator.asMap("1")).equalTo("1");
    that(aggregator.asMap(1.1)).equalTo(1.1);
    that(aggregator.asMap(true)).equalTo(true);
    that(aggregator.asMap(false)).equalTo(false);
  }
}
