package io.immutables.micro.fact;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.immutables.micro.ExceptionSink;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import static java.util.stream.Collectors.toUnmodifiableSet;

public interface QueryParser {

  Splitter SPLITTER = Splitter.on("/");
  Splitter VALUE_SPLITTER = Splitter.on(",");
  Pattern VALUES_PATTERN = Pattern.compile("\\{([^{}]+)}");
  Pattern LABELS_PATTERN = Pattern.compile("@([^@{}!*#?$]+)");
  Pattern REF_PATTERN = Pattern.compile("#([^@{}!*#?]+)");
  Pattern EMPTY_PARAMETERS = Pattern.compile("[@#{][@#{}!?*]|[@#{]$");
  Pattern INVALID_SELECTOR = Pattern.compile("[?!](?!$)|\\*(?!$|\\*$)");

  //[@#{][@#{}!?*]|[@#{]$|[?!](?!$)|\*(?!(?:$|\*$))

  QueryNode parse(String query);

  static QueryParser queryParser(ExceptionSink sink) {
    return new QueryParser() {
      @Override
      public QueryNode parse(String query) {
        try {
          return parseQuery(query);
        } catch (Exception e) {
          sink.unhandled(e);
          return QueryNode.single(
              ImmutableList.of(QueryNode.Filter.label(ImmutableList.of("error", Strings.nullToEmpty(e.getMessage())))),
              Optional.empty());
        }
      }

      private QueryNode parseQuery(String query) {
        QueryNode node = null;
        for (String nodeQuery : Lists.reverse(SPLITTER.splitToList(query))) {
          node = parseNode(nodeQuery, node);
        }

        long rootNodeRefs = node.filters().stream()
            .filter(QueryNode.Reference.class::isInstance)
            .mapToLong(filter -> ((QueryNode.Reference) filter).refs().size())
            .sum();
        if (rootNodeRefs == 0) {
          node = node.add(QueryNode.Filter.reference(List.of(0L)));
        }
        if (rootNodeRefs > 1) {
          throw new UnsupportedOperationException("Root node supports only zero or one reference query parameter.");
        }

        return node;
      }

      private QueryNode parseNode(String nodeQuery, QueryNode prev) {

        if (EMPTY_PARAMETERS.matcher(nodeQuery).results().findAny().isPresent()) {
          throw new IllegalArgumentException("Cannot use label '@', reference '#', value '{}' without parameter.");
        }
        if (INVALID_SELECTOR.matcher(nodeQuery).results().findAny().isPresent()) {
          throw new IllegalArgumentException("Selector (*, **, ?, !) should be at the end of node query.");
        }

        long openBrace = nodeQuery.chars().filter(ch -> ch == '{').count();
        long closingBrace = nodeQuery.chars().filter(ch -> ch == '}').count();
        if (openBrace > 1 || openBrace != closingBrace) {
          throw new IllegalArgumentException("Values '{}' could be provided only once.");
        }

        Set<String> values = VALUES_PATTERN.matcher(nodeQuery).results()
            .map(MatchResult::group)
            .map(g -> g.replace("{", "").replace("}", ""))
            .flatMap(g -> VALUE_SPLITTER.splitToList(g).stream())
            .collect(toUnmodifiableSet());

        Set<String> labels = LABELS_PATTERN.matcher(nodeQuery).results()
            .map(MatchResult::group)
            .map(g -> g.replace("@", ""))
            .collect(toUnmodifiableSet());

        Set<Long> refs = REF_PATTERN.matcher(nodeQuery).results()
            .map(MatchResult::group)
            .map(g -> g.replace("#", ""))
            .map(Long::parseLong)
            .collect(toUnmodifiableSet());

        Set<QueryNode.Filter> filters = new HashSet<>();
        if (values.size() > 0) filters.add(QueryNode.Filter.value(values));
        if (labels.size() > 0) filters.add(QueryNode.Filter.label(labels));
        if (refs.size() > 0) filters.add(QueryNode.Filter.reference(refs));

        if (nodeQuery.endsWith("$?")) return QueryNode.any(filters, Optional.ofNullable(prev)).withByValue(true);
        if (nodeQuery.endsWith("$!")) return QueryNode.single(filters, Optional.ofNullable(prev)).withByValue(true);
        if (nodeQuery.endsWith("$")) return QueryNode.all(filters, Optional.ofNullable(prev)).withByValue(true);
        if (nodeQuery.endsWith("?")) return QueryNode.any(filters, Optional.ofNullable(prev));
        if (nodeQuery.endsWith("!")) return QueryNode.single(filters, Optional.ofNullable(prev));
        if (nodeQuery.endsWith("**")) return QueryNode.allAndDescendant(filters, Optional.ofNullable(prev));
        return QueryNode.all(filters, Optional.ofNullable(prev));
      }
    };
  }
}
