package io.immutables.micro.fact;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.immutables.Nullable;
import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.micro.ExceptionSink;
import javax.annotation.concurrent.GuardedBy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import static com.google.common.base.Preconditions.checkArgument;
import static io.immutables.micro.fact.QueryNode.Reference;
import static io.immutables.micro.fact.QueryNode.SelectorType.ALL_DESCENDANT;
import static io.immutables.micro.fact.QueryNode.SelectorType.ANY;
import static io.immutables.micro.fact.QueryNode.SelectorType.SINGLE;
import static io.immutables.micro.fact.ReportEntry.Kind.Q;
import static io.immutables.micro.fact.ReportEntry.Kind.V;

public class InMemoryReportAggregator implements ReportSink {
  static final class EntryNode {
    ReportEntry entry;
    boolean discontinued = false;
    int since = 0;
    EntryNode parent;
    final ArrayList<EntryNode> children = Lists.newArrayList();

    EntryNode(ReportEntry entry) {
      this.entry = entry;
    }

    @Override public String toString() {
      return "#" + entry.id()
          + (!entry.label().isEmpty() ? "@" + entry.label() : "")
          + "{" + entry.value() + "}";
    }
  }

  private final ExceptionSink exception;
  private final Codec<Object> objectCodec;
  private final OkJson coder;
  private final ReadWriteLock lock;

  @GuardedBy("lock") private Map<Long, EntryNode> tree;
  @GuardedBy("lock") private ArrayDeque<Long> discontinuedNodes;
  @GuardedBy("lock") private int updateCount;

  public InMemoryReportAggregator(ExceptionSink exception, OkJson coder) {
    this.exception = exception;
    this.coder = coder;
    this.objectCodec = coder.get(Object.class);
    this.lock = new ReentrantReadWriteLock();
    this.reset();
  }

  @Override public void report(Iterable<ReportEntry> entries) {
    // accept batch reports
    lock.writeLock().lock();
    this.updateCount++;
    try {
      entries.forEach(this::accept);
    } catch (Exception e) {
      exception.unhandled(e);
    } finally {
      lock.writeLock().unlock();
    }

    // FIXME think about read lock escalation
    // flush discontinued nodes
    lock.writeLock().lock();
    try {
      if (discontinuedNodes.size() > FLUSH_THRESHOLD) {
        discontinuedNodes.removeIf(this::removeIfOk);
      }
    } catch (Exception e) {
      exception.unhandled(e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private boolean removeIfOk(long id) {
    var node = tree.get(id);
    if (node != null) {
      if (updateCount < node.since + SINCE_THRESHOLD) {
        // HACK to actually consider nodes removable if they older than threshold
        return false;
      }
      var parent = node.parent;
      if (parent != null) {
        parent.children.remove(node);
        node.parent = null; // should not cause non-GC'ed cycle, but just to short circuit such checks
      }
			// making sure it's removed anyway
			tree.remove(id);
			return true;
    } else {
      // cannot be null, hesitate to insert assertion though
			return false;
    }
  }

  @Nullable
  public OutputNode get(long id) {
    lock.readLock().lock();
    try {
      var node = tree.get(id);
      return node == null || node.
          discontinued ? null :
          OutputNode.of(
              node.entry.id(),
              node.entry.label(),
              node.entry.value(),
              node.children.stream()
                  .filter(n -> !n.discontinued)
                  .map(n -> get(n.entry.id()))
                  .collect(Collectors.toList()));
    } catch (Exception e) {
      exception.unhandled(e);
      throw e;
    } finally {
      lock.readLock().unlock();
    }
  }

  public void reset() {
    lock.writeLock().lock();
    try {
      this.tree = Maps.newHashMap();
      this.discontinuedNodes = new ArrayDeque<>(512);
      tree.put(ROOT_ID, new EntryNode(ReportEntry.of(ROOT_ID, ROOT_ID, Q, LABEL_ROOT, "", Instant.now())));
      this.updateCount = 0;
    } catch (Exception e) {
      exception.unhandled(e);
      throw e;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public int size() {
    lock.readLock().lock();
    try {
      return tree.size() - discontinuedNodes.size();
    } catch (Exception e) {
      exception.unhandled(e);
      throw e;
    } finally {
      lock.readLock().unlock();
    }
  }

  public Map<String, Object> find(QueryNode q) {
    return find(q, SINCE_EVER, false);
  }

  public Map<String, Object> find(QueryNode q, int since, boolean stripTracking) {
    lock.readLock().lock();
    try {
      var errors = new ArrayList<String>();
      var rootFilter = q.filters().stream()
          .filter(Reference.class::isInstance)
          .map(Reference.class::cast)
          .findAny();
      if (rootFilter.isEmpty()) {
        errors.add("Expecting reference filter for the root node");
      }
      var root = rootFilter.stream()
          .map(Reference::refs)
          .flatMap(refs -> refs.stream().map(tree::get))
          .findAny();

      var result = ImmutableMap.<String, Object>builder();
      root.ifPresent(r -> result.putAll(find(r, q, errors, since, stripTracking)));
      if (!errors.isEmpty()) {
        result.put("@errors", ImmutableList.copyOf(errors));
      }
      return result.build();
    } catch (Exception e) {
      exception.unhandled(e);
      throw e;
    } finally {
      lock.readLock().unlock();
    }
  }

  @GuardedBy("lock")
  private void accept(ReportEntry entry) {
    switch (entry.kind()) {
    case D:
      discontinue(entry);
      break;
    case V:
      introduce(entry, false);
      break;
    case Q:
      introduce(entry, true);
      break;
    case U: throw new UnsupportedOperationException("update");
    case E: throw new UnsupportedOperationException("element");
    case I: throw new UnsupportedOperationException("increment");
    }
  }

  @GuardedBy("lock")
  private void introduce(ReportEntry entry, boolean replaceUnique) {
    checkArgument(entry.id() != ROOT_ID, "Zero is reserved root entry id");

    // prepare created or updated node
    var node = tree.get(entry.id());
    boolean addingNew;
    if (node == null) {
      node = new EntryNode(entry);
      addingNew = true;
    } else {
      if (LABEL_AUTOPARENT.equals(node.entry.label())) {
        var nodeId = node.entry.id();
        tree.get(ROOT_ID).children.removeIf(n -> n.entry.id() == nodeId);
      } else {
        checkArgument(node.entry.parent() == entry.parent(), "Invalid parent id on update");
      }
      node.entry = entry;
      addingNew = false;
    }

    // add itself to parent
    var parent = tree.get(entry.parent());
    if (parent == null) {
      exception.consumed(new IllegalArgumentException("Introduce. Unknown parent id " + entry.parent()));
      // create temp parent
      parent = new EntryNode(ReportEntry.of(entry.parent(), ROOT_ID, V, LABEL_AUTOPARENT, 0, Instant.now()));
      parent.children.add(node);
      node.parent = parent;
      propagateUpdateSince(node);
      tree.put(parent.entry.id(), parent);
      tree.get(ROOT_ID).children.add(parent);
    } else if (parent.discontinued) {
      // no need to add child to discontinued parent
      node.discontinued = true;
      node.parent = parent;
      propagateUpdateSince(node);
      discontinuedNodes.addLast(node.entry.id());
    } else {
      // we update these and establish relationship even if already discontinued and will be cleaned up
      node.parent = parent;
      propagateUpdateSince(node);

      if (replaceUnique) {
        for (var c : parent.children) {
          if (c.entry.label().equals(entry.label())
              && Objects.equals(c.entry.value(), entry.value())) {
            discontinue(c.entry);
          }
        }
      }
      if (addingNew) {
        parent.children.add(node);
      }
    }
    // commit update
    tree.put(entry.id(), node);
  }

  private void propagateUpdateSince(EntryNode node) {
    for (var n = node; n != null; n = n.parent) {
      if (n.since == updateCount) break;
      n.since = updateCount;
    }
  }

  @GuardedBy("lock")
  private void discontinue(ReportEntry entry) {
    checkArgument(entry.id() != 0, "Zero is reserved root entry id");

    var node = tree.get(entry.id());
    if (node == null) {
      exception.consumed(new IllegalArgumentException("Discontinue. Unknown entry id " + entry.id()));
      return;
    }

    // update and mark as discontinued
    node.entry = entry;
    node.discontinued = true;
    propagateUpdateSince(node);
    discontinuedNodes.addLast(entry.id());

    // recursively for children
    for (var e : node.children) {
      discontinue(e.entry);
    }
  }

  @GuardedBy("lock")
  private Map<String, Object> find(EntryNode current, QueryNode q, List<String> errors, int since, boolean stripTracking) {
    var result = new LinkedHashMap<String, Object>();

    // skip not updated since
    if (current.since < since) return result;

    // apply filters
    if (!q.test(current.entry)) return result;

    // skip discontinued
    if (current.discontinued) {
      if (since > SINCE_EVER) {
        result.put("@id", Long.toString(current.entry.id()));
        result.put("@label", current.entry.label());
        if (!stripTracking) {
          result.put("@since", current.since);
        }
        result.put("@discontinued", true);
      }
      return result;
    }

    result.put("@id", Long.toString(current.entry.id()));
    result.put("@label", current.entry.label());
    if (!stripTracking) {
      result.put("@since", current.since);
      result.put("@count", current.children.stream().filter(c -> !c.discontinued).count());
    }
    result.put("$", current.entry.value());

    // recursively to the next level
    if (q.next().isPresent() || q.selector().equals(ALL_DESCENDANT)) {
      var kidResultsByLabel = ArrayListMultimap.<String, Map<String, Object>>create();
      for (EntryNode kid : current.children) {
        // q.next is empty only for AllAndDescendant, repeat it till the end
        var kidResult = find(kid, q.next().orElse(q), errors, since, stripTracking);
        if (!kidResult.isEmpty()) {
          kidResultsByLabel.put(kid.entry.label(), kidResult);
        }

        if (!q.selector().equals(ALL_DESCENDANT)
            && q.next().get().selector().equals(SINGLE))

          // checks for selector type
          if (!q.selector().equals(ALL_DESCENDANT)
              && q.next().get().selector().equals(ANY)
              && kidResultsByLabel.size() == 1) {
            break;
          }

        if (!q.selector().equals(ALL_DESCENDANT)
            && q.next().get().selector().equals(SINGLE)
            && kidResultsByLabel.size() > 1) {
          errors.add(String.format("Node %d. Single child element expected", current.entry.id()));
          return result;
        }
      }

      if (!q.selector().equals(ALL_DESCENDANT)
          && q.next().get().selector().equals(SINGLE)
          && kidResultsByLabel.size() != 1) {
        errors.add(String.format("Node %d. Single child element expected", current.entry.id()));
        return result;
      }

      // force empty labels array if asked for
      if (q.next().isPresent()
          && !(q.next().get().selector().equals(SINGLE) || q.next().get().selector().equals(ANY))) {
        q.next().get().filters().stream()
            .filter(QueryNode.Label.class::isInstance)
            .map(QueryNode.Label.class::cast)
            .flatMap(l -> l.labels().stream())
            .filter(l -> !kidResultsByLabel.containsKey(l))
            .forEach(l -> result.put(l, List.of()));
      }

      // group output by kid's label
      for (String label : kidResultsByLabel.keySet()) {
        var group = kidResultsByLabel.get(label);
        //if (group.size() == 1)
        if (!q.selector().equals(ALL_DESCENDANT)
            && (q.next().get().selector().equals(SINGLE) || q.next().get().selector().equals(ANY))) {
          if (q.next().orElse(q).byValue()) {
            result.put(label, group.get(0).get("$"));
          } else {
            result.put(label, group.get(0));
          }
        } else {//if (group.size() > 1)
          if (q.next().orElse(q).byValue()) {
            result.put(label, group.stream().map(m -> m.get("$")).collect(Collectors.toList()));
          } else {
            result.put(label, group);
          }
        }
      }
    }

    return result;
  }

  Object asMap(Object value) {
    return coder.fromJson(coder.toJson(value, objectCodec), objectCodec);
  }

  private static final int FLUSH_THRESHOLD = 256;
  private static final int SINCE_THRESHOLD = 1000;
  public static final int SINCE_EVER = -1;
  public static final long ROOT_ID = 0L;
  public static final String LABEL_ROOT = "<root>";
  public static final String LABEL_AUTOPARENT = "<orphans>";
}
