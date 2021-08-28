package io.immutables.micro.fact;

import io.immutables.codec.OkJson;
import io.immutables.micro.CodecsFactory;
import io.immutables.micro.ExceptionSink;
import org.junit.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static io.immutables.micro.fact.ReportEntry.Kind.D;
import static io.immutables.micro.fact.ReportEntry.Kind.V;
import static io.immutables.that.Assert.that;

//
//
//                                                     30 --> @a{foo}
//                            20 --> @a{true}
//                                                     31 --> @a{bar}
//
//
//
//           10 --> @a{1}     21 --> @a{false}        x32 --> @b{bar}
//
//
//
//
//                            22 --> @b{false}
//
//
//
//  0
//
//
//                                                     33 --> {-1}
//                            23 --> {1.5}
//                                                     34 --> {-1.5}
//
//           11 --> @b{2}
//
//                                                    x35 --> {0}
//                           x24 --> @a{0.5}
//                                                    x36 --> {0.0}
//
public class InMemoryReportAggregatorQueryTest {
  static final OkJson coder = OkJson.configure(c -> c.add(new CodecsFactory()));
  static final ExceptionSink sink = ExceptionSink.assertNoUnhandled();
  final InMemoryReportAggregator aggregator = new InMemoryReportAggregator(sink,coder);
  final QueryParser queryParser = QueryParser.queryParser(sink);
  {
    aggregator.report(ReportEntry.of(10, 0, V, "a", 1, Instant.now()));
    aggregator.report(ReportEntry.of(11, 0, V, "b", 2, Instant.now()));
    aggregator.report(ReportEntry.of(20, 10, V, "a", true, Instant.now()));
    aggregator.report(ReportEntry.of(21, 10, V, "a", false, Instant.now()));
    aggregator.report(ReportEntry.of(22, 10, V, "b", false, Instant.now()));
    aggregator.report(ReportEntry.of(23, 11, V, "", 1.5, Instant.now()));
    aggregator.report(ReportEntry.of(24, 11, V, "a", 0.5, Instant.now()));
    aggregator.report(ReportEntry.of(24, 11, D, "a", 0.5, Instant.now()));
    aggregator.report(ReportEntry.of(30, 20, V, "a", "foo", Instant.now()));
    aggregator.report(ReportEntry.of(31, 20, V, "a", "bar", Instant.now()));
    aggregator.report(ReportEntry.of(32, 21, V, "b", "bar", Instant.now()));
    aggregator.report(ReportEntry.of(32, 21, D, "b", "bar", Instant.now()));
    aggregator.report(ReportEntry.of(33, 23, V, "", -1, Instant.now()));
    aggregator.report(ReportEntry.of(34, 23, V, "", -1.5, Instant.now()));
    aggregator.report(ReportEntry.of(35, 24, V, "", 0, Instant.now()));
    aggregator.report(ReportEntry.of(36, 24, V, "", 0.0, Instant.now()));
  }
  @Test
  public void canFindNodeById() {
    that(aggregator.find(queryParser.parse("")).get("@id")).equalTo("0");
    that(aggregator.find(queryParser.parse("#0")).get("@id")).equalTo("0");
    that(aggregator.find(queryParser.parse("#10")).get("@id")).equalTo("10");
    that(aggregator.find(queryParser.parse("#34")).get("@id")).equalTo("34");
  }

  @Test
  public void cantFindDiscontinued() {
    that(aggregator.find(queryParser.parse("#24")).entrySet()).isEmpty();
    that(aggregator.find(queryParser.parse("#35")).entrySet()).isEmpty();
    that(aggregator.find(queryParser.parse("#36")).entrySet()).isEmpty();
    that(aggregator.find(queryParser.parse("#32")).entrySet()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void canSelectAllChildrenByFilter() {
    var result = aggregator.find(queryParser.parse("#10/*"));
    that(result.get("@id")).equalTo("10");
    that(result.get("@label")).equalTo("a");
    that(result.get("$")).equalTo(1);
    that(result.get("a")).is(v -> ((List<Map<String, Object>>) v).size() == 2);
    that(result.get("b")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("22"));

    var resultValueTrue = aggregator.find(queryParser.parse("#10/{yes}"));
    that(resultValueTrue.get("@id")).equalTo("10");
    that(resultValueTrue.get("@label")).equalTo("a");
    that(resultValueTrue.get("$")).equalTo(1);
    that(resultValueTrue.get("a")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("20"));

    var resultValueFalse = aggregator.find(queryParser.parse("#10/{false}"));
    that(resultValueFalse.get("@id")).equalTo("10");
    that(resultValueFalse.get("@label")).equalTo("a");
    that(resultValueFalse.get("$")).equalTo(1);
    that(resultValueFalse.get("a")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("21"));
    that(resultValueFalse.get("b")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("22"));

    var resultLabelA = aggregator.find(queryParser.parse("#10/@a"));
    that(resultLabelA.get("@id")).equalTo("10");
    that(resultLabelA.get("@label")).equalTo("a");
    that(resultLabelA.get("$")).equalTo(1);
    that(resultLabelA.get("a")).is(v -> ((List<Map<String, Object>>) v).size() == 2);

    var resultLabelB = aggregator.find(queryParser.parse("#10/@b"));
    that(resultLabelB.get("@id")).equalTo("10");
    that(resultLabelB.get("@label")).equalTo("a");
    that(resultLabelB.get("$")).equalTo(1);
    that(resultLabelB.get("b")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("22"));

    var resultValueAndLabel = aggregator.find(queryParser.parse("#10/@a{0}"));
    that(resultValueAndLabel.get("@id")).equalTo("10");
    that(resultValueAndLabel.get("@label")).equalTo("a");
    that(resultValueAndLabel.get("$")).equalTo(1);
    that(resultValueAndLabel.get("a")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("21"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void canSelectAnyChildByFilter() {
    var resultValue = aggregator.find(queryParser.parse("#0/{2}?"));
    that(resultValue.get("@id")).equalTo("0");
    that(resultValue.get("b")).is(v -> ((Map<String, Object>) v).get("@id").equals("11"));

    var resultLabel = aggregator.find(queryParser.parse("#0/@a?"));
    that(resultLabel.get("@id")).equalTo("0");
    that(resultLabel.get("a")).is(v -> ((Map<String, Object>) v).get("@id").equals("10"));

    that(aggregator.find(queryParser.parse("#0/@a{3}?")).get("")).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void canSelectSingleByFilter() {
    var resultSingle = aggregator.find(queryParser.parse("#11/!"));
    that(resultSingle.get("@id")).equalTo("11");
    that(resultSingle.get("")).is(v -> ((Map<String, Object>) v).get("@id").equals("23"));
    that(resultSingle.get("")).is(v -> ((Map<String, Object>) v).get("$").equals(1.5));

    var resultWrongValue = aggregator.find(queryParser.parse("#11/{1.6}!"));
    that(resultWrongValue.get("@id")).equalTo("11");
    that(resultWrongValue.get("")).isNull();
    that(resultWrongValue.get("@errors"))
        .is(v -> ((List<String>) v).get(0).equals("Node 11. Single child element expected"));

    var resultValue = aggregator.find(queryParser.parse("#11/{1.5}!"));
    that(resultValue.get("@id")).equalTo("11");
    that(resultValue.get("")).is(v -> ((Map<String, Object>) v).get("@id").equals("23"));
    that(resultValue.get("")).is(v -> ((Map<String, Object>) v).get("$").equals(1.5));

    var resultLabel = aggregator.find(queryParser.parse("#11/@a!"));
    that(resultLabel.get("@id")).equalTo("11");
    that(resultLabel.get("@label")).equalTo("b");
    that(resultLabel.get("$")).equalTo(2);
    that(resultLabel.get("a")).isNull();
    that(resultWrongValue.get("@errors"))
        .is(v -> ((List<String>) v).get(0).equals("Node 11. Single child element expected"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void canFindAllDescendant() {
    var result = aggregator.find(queryParser.parse("/**"));
    that(result.get("@id")).equalTo("0");
    that(result.get("a")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("10"));
    var resultA = ((List<Map<String, Object>>) result.get("a")).get(0);
    that(resultA.get("a")).is(v -> ((List<Map<String, Object>>) v).size() == 2);
    var resultAA = (List<Map<String, Object>>) resultA.get("a");
    that(resultAA.get(0).get("a")).is(v -> ((List<Map<String, Object>>) v).size() == 2);
    var resultAAA = (List<Map<String, Object>>) resultAA.get(0).get("a");
    that(resultAAA.get(0)).is(v -> v.get("@id").equals("30"));
    that(resultAAA.get(1)).is(v -> v.get("@id").equals("31"));
    that(resultAA.get(1).get("b")).isNull();
    that(resultA.get("b")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("22"));

    that(result.get("b")).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("11"));
    var resultB = ((List<Map<String, Object>>) result.get("b")).get(0);
    var node = ((List<Map<String, Object>>) resultB.get("")).get(0).get("");
    that(node).is(v -> ((List<Map<String, Object>>) v).size() == 2);
    that(node).is(v -> ((List<Map<String, Object>>) v).get(0).get("@id").equals("33"));
    that(node).is(v -> ((List<Map<String, Object>>) v).get(1).get("@id").equals("34"));
  }
}
