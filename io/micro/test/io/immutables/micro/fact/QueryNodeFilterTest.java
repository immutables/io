package io.immutables.micro.fact;

import com.google.common.collect.ImmutableList;
import io.immutables.Nullable;
import io.immutables.micro.fact.QueryNode.Filter;
import org.immutables.value.Value.Immutable;
import org.junit.Test;
import java.time.Instant;
import java.util.Optional;
import static io.immutables.micro.fact.ReportEntry.Kind.V;
import static io.immutables.that.Assert.that;
import static org.immutables.value.Value.Parameter;

public class QueryNodeFilterTest {
  @Immutable
  public interface TestValue {
    @Parameter long id();
    @Parameter Optional<String> name();
    @Parameter @Nullable Double value();
  }

  @Test
  public void canFilterByReference() {
    var filter = Filter.reference(ImmutableList.of(1L, 2L, 3L));
    that(filter.test(ReportEntry.of(1, 0, V, "", new Object(), Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(2, 0, V, "", new Object(), Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(3, 0, V, "", new Object(), Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(0, 0, V, "", new Object(), Instant.now()))).is(false);
    var emptyFilter = Filter.reference(ImmutableList.of());
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", new Object(), Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(2, 0, V, "", new Object(), Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(3, 0, V, "", new Object(), Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(0, 0, V, "", new Object(), Instant.now()))).is(true);
  }

  @Test
  public void canFilterByLabel() {
    var filter = Filter.label(ImmutableList.of("a", "b"));
    that(filter.test(ReportEntry.of(1, 0, V, "a", 0, Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "b", 0, Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "c", 0, Instant.now()))).is(false);
    that(filter.test(ReportEntry.of(1, 0, V, "", 0, Instant.now()))).is(false);
    var emptyFilter = Filter.label(ImmutableList.of());
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "a", 0, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "b", 0, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "c", 0, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", 0, Instant.now()))).is(true);
  }

  @Test
  public void canFilterNode() {
    var q = QueryNode.any(
        ImmutableList.of(
            Filter.reference(ImmutableList.of(1L, 2L, 3L)),
            Filter.value(ImmutableList.of("true", "-1.5", "str", "10")),
            Filter.label(ImmutableList.of("a", "b"))
        ),
        Optional.empty()
    );
    that(q.test(ReportEntry.of(1, 0, V, "a", 10, Instant.now()))).is(true);
    that(q.test(ReportEntry.of(2, 0, V, "b", true, Instant.now()))).is(true);
    that(q.test(ReportEntry.of(2, 0, V, "c", true, Instant.now()))).is(false);
    that(q.test(ReportEntry.of(2, 0, V, "a", false, Instant.now()))).is(true);
    that(q.test(ReportEntry.of(5, 0, V, "a", true, Instant.now()))).is(false);
  }

  @Test
  public void canFilterByValue() {
    var filter = Filter.value(ImmutableList.of("true", "-1.5", "str", "10"));
    that(filter.test(ReportEntry.of(1, 0, V, "", true, Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "", -1.5, Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "", "str", Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "", 10, Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "", "10", Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "", "-1.5", Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "", false, Instant.now()))).is(true);
    that(filter.test(ReportEntry.of(1, 0, V, "", -1, Instant.now()))).is(false);
    that(filter.test(ReportEntry.of(1, 0, V, "", "", Instant.now()))).is(false);
    var emptyFilter = Filter.value(ImmutableList.of());
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", true, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", -1.5, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", "str", Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", 10, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", "10", Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", "-1.5", Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", false, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", -1, Instant.now()))).is(true);
    that(emptyFilter.test(ReportEntry.of(1, 0, V, "", "", Instant.now()))).is(true);
  }

  @Test
  public void canFilterListByValue() {
    var filter1 = Filter.value(ImmutableList.of("1", "2", "3"));
    that(filter1.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(true);
    that(filter1.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 4, 5, 6), Instant.now()))).is(true);
    that(filter1.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(3), Instant.now()))).is(true);
    that(filter1.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(4, 5, 6), Instant.now()))).is(false);

    var filter2 = Filter.value(ImmutableList.of("1&2", "3"));
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 4), Instant.now()))).is(true);
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 4), Instant.now()))).is(false);
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(2, 4), Instant.now()))).is(false);
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(4), Instant.now()))).is(false);
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(3), Instant.now()))).is(true);
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 3), Instant.now()))).is(true);
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(2, 3), Instant.now()))).is(true);
  }

  @Test
  public void canFilterObjectByField() {
    var filter1 = Filter.value(ImmutableList.of(":"));
    that(filter1.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(false);
    that(filter1.test(ReportEntry.of(1, 0, V, "", 1, Instant.now()))).is(false);
    that(filter1.test(ReportEntry.of(1, 0, V, "", true, Instant.now()))).is(false);
    that(filter1.test(ReportEntry.of(1, 0, V, "", new Object(), Instant.now()))).is(true);
    that(filter1.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.of("v1"), 0.0), Instant.now()))).is(true);

    var filter2 = Filter.value(ImmutableList.of("id:"));
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(false);
    that(filter2.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.of("v1"), 0.0), Instant.now()))).is(true);

    var filter3 = Filter.value(ImmutableList.of("parent:"));
    that(filter3.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(false);
    that(filter3.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.of("v1"), 0.0), Instant.now()))).is(false);

    var filter4 = Filter.value(ImmutableList.of("name:*"));
    that(filter4.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(false);
    that(filter4.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.empty(), 0.0), Instant.now()))).is(false);
    that(filter4.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.of("v1"), 0.0), Instant.now()))).is(true);

    var filter5 = Filter.value(ImmutableList.of("value:*"));
    that(filter5.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(false);
    that(filter5.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.empty(), null), Instant.now()))).is(false);
    that(filter5.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.empty(), 0.0), Instant.now()))).is(true);

    var filter6 = Filter.value(ImmutableList.of("id:1"));
    that(filter6.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(false);
    that(filter6.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.empty(), null), Instant.now()))).is(true);
    that(filter6.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(0, Optional.empty(), null), Instant.now()))).is(false);

    var filter7 = Filter.value(ImmutableList.of("id:1&name:v1"));
    that(filter7.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(false);
    that(filter7.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.of("v1"), null), Instant.now()))).is(true);
    that(filter7.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(2, Optional.of("v1"), null), Instant.now()))).is(false);
    that(filter7.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.empty(), null), Instant.now()))).is(false);

    var filter8 = Filter.value(ImmutableList.of("id:1&name:v1", "id:2", "value:*", "3"));
    that(filter8.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2, 3), Instant.now()))).is(true);
    that(filter8.test(ReportEntry.of(1, 0, V, "", ImmutableList.of(1, 2), Instant.now()))).is(false);
    that(filter8.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.of("v1"), null), Instant.now()))).is(true);
    that(filter8.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(2, Optional.of("v1"), null), Instant.now()))).is(true);
    that(filter8.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(3, Optional.of("v1"), 1.0), Instant.now()))).is(true);
    that(filter8.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.of("v2"), null), Instant.now()))).is(false);
    that(filter8.test(ReportEntry.of(1, 0, V, "", ImmutableTestValue.of(1, Optional.empty(), null), Instant.now()))).is(false);
  }
}
