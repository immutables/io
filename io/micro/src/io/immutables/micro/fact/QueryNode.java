package io.immutables.micro.fact;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Doubles;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static com.google.common.base.Preconditions.checkState;
import static io.immutables.micro.fact.QueryNode.SelectorType.ALL;
import static io.immutables.micro.fact.QueryNode.SelectorType.ALL_DESCENDANT;
import static io.immutables.micro.fact.QueryNode.SelectorType.ANY;
import static io.immutables.micro.fact.QueryNode.SelectorType.SINGLE;
import static org.immutables.value.Value.Parameter;

@Enclosing
@Immutable
public abstract class QueryNode implements Predicate<ReportEntry> {
  @Parameter
  public abstract Set<Filter> filters();

  @Parameter
  public abstract Optional<QueryNode> next();

  @Parameter
  public abstract SelectorType selector();

  public enum SelectorType {
    ALL, ANY, SINGLE, ALL_DESCENDANT
  }

  @Default
  public boolean byValue() {
    return false;
  }

  abstract QueryNode withByValue(boolean byValue);

  @Check
  void check() {
    if (selector().equals(SelectorType.ALL_DESCENDANT)) {
      checkState(filters().isEmpty(), "`ALL_DESCENDANT` should be without filters");
      checkState(next().isEmpty(), "`ALL_DESCENDANT` should be last");
      checkState(!byValue(), "`ALL_DESCENDANT` should be not by value");
    }
  }

  @Override
  public boolean test(ReportEntry entry) {
    var result = true;

    for (Filter filter : filters()) {
      result &= filter.test(entry);
    }

    return result;
  }

  public static QueryNode all(Iterable<QueryNode.Filter> filters, Optional<QueryNode> next) {
    return ImmutableQueryNode.of(filters, next, ALL);
  }

  public static QueryNode any(Iterable<QueryNode.Filter> filters, Optional<QueryNode> next) {
    return ImmutableQueryNode.of(filters, next, ANY);
  }

  public static QueryNode single(Iterable<QueryNode.Filter> filters, Optional<QueryNode> next) {
    return ImmutableQueryNode.of(filters, next, SINGLE);
  }

  public static QueryNode allAndDescendant(Iterable<QueryNode.Filter> filters, Optional<QueryNode> next) {
    return ImmutableQueryNode.of(filters, next, ALL_DESCENDANT);
  }

  public QueryNode add(QueryNode.Filter filter) {
    return ((ImmutableQueryNode) this).withFilters(
        ImmutableList.<Filter>builder().addAll(this.filters()).add(filter).build());
  }

  public interface Filter extends Predicate<ReportEntry> {
    static QueryNode.Reference reference(Iterable<Long> refs) {
      return ImmutableQueryNode.Reference.of(refs);
    }

    static QueryNode.Value value(Iterable<String> values) {
      return ImmutableQueryNode.Value.of(values);
    }

    static QueryNode.Label label(Iterable<String> labels) {
      return ImmutableQueryNode.Label.of(labels);
    }
  }

  @Immutable
  public abstract static class Reference implements Filter {
    @Parameter
    public abstract Set<Long> refs();

    @Override
    public boolean test(ReportEntry entry) {
      return refs().isEmpty() || refs().contains(entry.id());
    }
  }

  @Immutable
  public abstract static class Value implements Filter {
    private static final ImmutableSet<String> TRUE_SET = ImmutableSet.of("y", "yes", "1", "true");
    private static final String ANY_VALUE = "*";
    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final Splitter KEY_VALUE_SPLITTER = Splitter.on(KEY_VALUE_SEPARATOR).omitEmptyStrings();
    private static final Splitter AND_SPLITTER = Splitter.on('&');

    @Parameter
    public abstract Set<String> values();

    @Override
    public boolean test(ReportEntry entry) {
      if (values().isEmpty()) { return true; }

      for (String filter : values()) {
        if (entry.value() instanceof List) {
          if (testListConjunction(filter, (List<?>) entry.value())) { return true; }
        } else {
          if (testScalarOrObject(filter, entry.value())) { return true; }
        }
      }

      return false;
    }

    private static boolean testScalarOrObject(String filter, Object value) {
      if (value instanceof Optional && ((Optional<?>) value).isEmpty()) { return false; }

      @SuppressWarnings("OptionalGetWithoutIsPresent")
      var valueTotTest = value instanceof Optional ? ((Optional<?>) value).get() : value;
      if (valueTotTest instanceof Double) {
        var filterValue = Doubles.tryParse(filter);
        return filterValue != null && filterValue.compareTo((Double) valueTotTest) == 0;
      } else if (valueTotTest instanceof Number) {
        return filter.equals(valueTotTest.toString());
      } else if (valueTotTest instanceof Boolean) {
        var filterValue = TRUE_SET.contains(filter.toLowerCase());
        return filterValue == (Boolean) valueTotTest;
      } else if (valueTotTest instanceof String) {
        return filter.equals(valueTotTest.toString());
      } else {
        return testObjectConjunction(filter, valueTotTest);
      }
    }

    private static boolean testListConjunction(String filter, List<?> value) {
      var filterValues = AND_SPLITTER.splitToList(filter);
      if (filterValues.isEmpty()) { return false; }

      return filterValues.stream().allMatch(filterValue -> testList(filterValue, value));
    }

    private static boolean testList(String filter, List<?> value) {
      return value.stream().anyMatch(v -> testScalarOrObject(filter, v));
    }

    private static boolean testObjectConjunction(String filter, Object value) {
      var filterValues = AND_SPLITTER.splitToList(filter);
      if (filterValues.isEmpty()) { return false; }

      return filterValues.stream().allMatch(filterValue -> testObject(filterValue, value));
    }

    private static boolean testObject(String filter, Object value) {
      if (!filter.contains(KEY_VALUE_SEPARATOR)) return filter.equals(value.toString());

      var keyValueFilter = KEY_VALUE_SPLITTER.splitToList(filter);
      // value is object
      if (keyValueFilter.isEmpty()) {
        return true;
      }

      var fieldValue = extractValue(keyValueFilter.get(0), value);

      // field exists
      if (keyValueFilter.size() == 1) {
        return fieldValue.isPresent();
      }
      // any value (not null / is present)
      if (keyValueFilter.size() == 2 && keyValueFilter.get(1).equals(ANY_VALUE)) {
        if (fieldValue.isPresent()
            && fieldValue.get().getValue() != null
            && fieldValue.get().getValue() instanceof Optional) {
          return ((Optional<?>) fieldValue.get().getValue()).isPresent();
        }
        return fieldValue.isPresent() && fieldValue.get().getValue() != null;
      }
      // field by value
      return fieldValue.isPresent() && testScalarOrObject(keyValueFilter.get(1), fieldValue.get().getValue());
    }

    static Optional<Map.Entry<String, Object>> extractValue(String fieldName, Object value) {
      try {
        if (value instanceof Map<?, ?>) {
          @SuppressWarnings("unchecked") var fieldValue = ((Map<Object, Object>) value).get(fieldName);
          return Optional.of(new AbstractMap.SimpleImmutableEntry<>(fieldName, fieldValue));
        } else {
          var accessor = Arrays.stream(value.getClass().getDeclaredMethods())
              .filter(m -> m.getName().equals(fieldName))
              .findAny();

          if (accessor.isEmpty()) return Optional.empty();

          Object fieldValue = accessor.get().invoke(value);
          return Optional.of(new AbstractMap.SimpleImmutableEntry<>(fieldName, fieldValue));
        }
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Immutable
  public abstract static class Label implements Filter {
    @Parameter
    public abstract Set<String> labels();

    @Override
    public boolean test(ReportEntry entry) {
      return labels().isEmpty() || labels().contains(entry.label());
    }
  }
}
