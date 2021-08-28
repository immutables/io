package io.immutables.micro.fact;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static io.immutables.micro.fact.ReportEntry.Kind.D;
import static io.immutables.micro.fact.ReportEntry.Kind.V;
import static io.immutables.that.Assert.that;

public class ReportTest {
  final List<ReportEntry> entries = new ArrayList<>();
  final Reporter root = Reporter.root(Clock.systemDefaultZone(), e -> entries.addAll(ImmutableList.copyOf(e)), Reporter.randomIds());

  @Test
  public void reportBasic() {
    var a = root.as("a").put(1);
    var b = root.as("b").put(2);
    that(a.label()).is("a");
    that(b.label()).is("b");
    b.update(3);
    b.close();
    a.close();
    that(entries).hasSize(5);
    var e0 = entries.get(0);
    var e1 = entries.get(1);
    var e2 = entries.get(2);
    var e3 = entries.get(3);
    var e4 = entries.get(4);
    for (var e : entries) {
      that(e.parent()).is(0);
      that(e.id()).just().notEqual(0);
    }
    that(e0.label()).is("a");
    that(e1.label()).is("b");
    that(e2.label()).is("b");
    that(e3.label()).is("b");
    that(e4.label()).is("a");

    that(e0.kind()).equalTo(V);
    that(e1.kind()).equalTo(V);
    that(e2.kind()).equalTo(V);
    that(e3.kind()).equalTo(D);
    that(e4.kind()).equalTo(D);

    that(e0.value()).equalTo(1);
    that(e1.value()).equalTo(2);
    that(e2.value()).equalTo(3);
    that(e3.value()).equalTo(3);
    that(e4.value()).equalTo(1);
  }

  @Test
  public void reportLabeledMap() {
    var a = root.as("a").reportMap();
    var b = root.as("b").reportMap();
//    that(a.label()).is("a");
//    that(b.label()).is("b");
    a.put(111, "aaa");
    a.put(111, "ccc");
    b.put(222, "bbb");
    a.close();
    b.close();
    that(entries).hasSize(5);
    var e0 = entries.get(0);
    var e1 = entries.get(1);
    var e2 = entries.get(2);
    var e3 = entries.get(3);
    var e4 = entries.get(4);
    for (var e : entries) {
      that(e.parent()).is(0);
      that(e.id()).just().notEqual(0);
    }
    that(e0.label()).is("a");
    that(e1.label()).is("a");
    that(e2.label()).is("b");
    that(e3.label()).is("a");
    that(e4.label()).is("b");

    that(e0.kind()).equalTo(V);
    that(e1.kind()).equalTo(V);
    that(e2.kind()).equalTo(V);
    that(e3.kind()).equalTo(D);
    that(e4.kind()).equalTo(D);

    that(e0.value()).equalTo("aaa");
    that(e1.value()).equalTo("ccc");
    that(e2.value()).equalTo("bbb");
    that(e3.value()).equalTo("ccc");
    that(e4.value()).equalTo("bbb");
  }

  @Test
  public void reportSet() {
    var one = root.as("").put("");
    var set = one.reportUnder().as("x").<String>reportSet();
    that(set.label()).is("x");
    set.add("a");
    set.add("b");
    set.reset(Set.of("a", "b", "c"));
    set.reset(Set.of("a"));
    set.close();

    that(entries.stream().map(ReportEntry::label)).isOf("", "x", "x", "x", "x", "x", "x");
    that(entries.stream().map(ReportEntry::value)).isOf("", "a", "b", "c", "b", "c", "a");
    that(entries.stream().map(ReportEntry::kind)).isOf(V, V, V, V, D, D, D);
  }

  @Test
  public void reportUnderSet() {
    var one = root.as("").put("");
    var set = one.reportUnder().as("x").<String>reportSet();
    var underSet = set.put("aaa").reportUnder().as("b").put("bbb");
    that(set.label()).is("x");
    that(underSet.label()).is("b");

    var e0 = entries.get(0);
    var e1 = entries.get(1);
    var e2 = entries.get(2);

    that(e0.label()).is("");
    that(e1.label()).is("x");
    that(e2.label()).is("b");

    that(e0.kind()).equalTo(V);
    that(e1.kind()).equalTo(V);
    that(e2.kind()).equalTo(V);

    that(e0.value()).equalTo("");
    that(e1.value()).equalTo("aaa");
    that(e2.value()).equalTo("bbb");

    that(e0.parent()).is(0);
    that(e1.parent()).is(e0.id());
    that(e2.parent()).is(e1.id());
  }

  @Test
  public void reportUnderLabeledMap() {
    var one = root.as("").put("");
    var map = one.reportUnder().as("x").<Integer, String>reportMap();
    var underMap = map.put(1, "aaa").reportUnder().as("b").put("bbb");
    map.put(1, "ccc");
    //that(map.label()).is("x");
    that(underMap.label()).is("b");

    var e0 = entries.get(0);
    var e1 = entries.get(1);
    var e2 = entries.get(2);
    var e3 = entries.get(3);

    that(e0.label()).is("");
    that(e1.label()).is("x");
    that(e2.label()).is("b");
    that(e3.label()).is("x");

    that(e0.kind()).equalTo(V);
    that(e1.kind()).equalTo(V);
    that(e2.kind()).equalTo(V);
    that(e3.kind()).equalTo(V);

    that(e0.value()).equalTo("");
    that(e1.value()).equalTo("aaa");
    that(e2.value()).equalTo("bbb");
    that(e3.value()).equalTo("ccc");

    that(e0.parent()).is(0);
    that(e1.parent()).is(e0.id());
    that(e2.parent()).is(e1.id());
    that(e3.parent()).is(e0.id());
  }

  @Test
  public void reportUnderNotLabeledMap() {
    var one = root.as("").put("");
    var map = one.reportUnder().<String>reportMap();
    var underMap = map.put("x", "aaa").reportUnder().as("b").put("bbb");
    map.put("y", "ccc");
    //that(map.label()).is("x");
    that(underMap.label()).is("b");

    var e0 = entries.get(0);
    var e1 = entries.get(1);
    var e2 = entries.get(2);
    var e3 = entries.get(3);

    that(e0.label()).is("");
    that(e1.label()).is("x");
    that(e2.label()).is("b");
    that(e3.label()).is("y");

    that(e0.kind()).equalTo(V);
    that(e1.kind()).equalTo(V);
    that(e2.kind()).equalTo(V);
    that(e3.kind()).equalTo(V);

    that(e0.value()).equalTo("");
    that(e1.value()).equalTo("aaa");
    that(e2.value()).equalTo("bbb");
    that(e3.value()).equalTo("ccc");

    that(e0.parent()).is(0);
    that(e1.parent()).is(e0.id());
    that(e2.parent()).is(e1.id());
    that(e3.parent()).is(e0.id());
  }

  @Test
  public void reportMap() {
    var one = root.as("").put("");
    var map = one.reportUnder().reportMap();
    map.put("a", "X");
    map.put("b", "Y");
    map.remove("b");
    map.put("a", "Z");
    map.close();

    that(entries.stream().map(ReportEntry::label)).isOf("", "a", "b", "b", "a", "a");
    that(entries.stream().map(ReportEntry::value)).isOf("", "X", "Y", "Y", "Z", "Z");
    that(entries.stream().map(ReportEntry::kind)).isOf(V, V, V, D, V, D);
  }

  @Test
  public void removeEldest() {
    var one = root.as("").put("");
    var set = one.reportUnder().as("x").<String>reportSet(2);
    that(set.limit()).is(2);
    set.add("a");
    set.add("b");
    set.add("c");
    set.add("d");

    var e0 = entries.get(0);
    var e1 = entries.get(1);
    var e2 = entries.get(2);
    var e3 = entries.get(3);
    var e4 = entries.get(4);
    var e5 = entries.get(5);
    var e6 = entries.get(6);

    that(e0.label()).is("");
    that(e1.label()).is("x");
    that(e2.label()).is("x");
    that(e3.label()).is("x");
    that(e4.label()).is("x");
    that(e5.label()).is("x");
    that(e6.label()).is("x");

    that(e0.kind()).equalTo(V);
    that(e1.kind()).equalTo(V);
    that(e2.kind()).equalTo(V);
    that(e3.kind()).equalTo(V);
    that(e4.kind()).equalTo(D);
    that(e5.kind()).equalTo(V);
    that(e6.kind()).equalTo(D);

    that(e0.value()).equalTo("");
    that(e1.value()).equalTo("a");
    that(e2.value()).equalTo("b");
    that(e3.value()).equalTo("c");
    that(e4.value()).equalTo("a");
    that(e5.value()).equalTo("d");
    that(e6.value()).equalTo("b");
  }
}
