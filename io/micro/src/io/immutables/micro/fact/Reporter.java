package io.immutables.micro.fact;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import io.immutables.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public interface Reporter {
  Labeled as(String label);

  <V> ReportedMap<String, V> reportMap();

  interface Report<V> extends AutoCloseable {
    long id();

    long parent();

    String label();

    V get();

    void update(V value);

    void close();

    Reporter reportUnder();
  }

  interface Labeled {
    <V> Report<V> put(V value);

    <V> Report<V> unique(V value);

    <E> ReportedSet<E> reportSet();

    <E> ReportedSet<E> reportSet(Integer limit);

    <K, V> ReportedMap<K, V> reportMap();
  }

  @NotThreadSafe
  interface ReportedSet<E> extends Iterable<E>, AutoCloseable {
    String label();

    Integer limit();

    boolean add(E value);

    boolean remove(E element);

    void removeAt(int index);

    E get(int index);

    Report<E> put(E value);

    Report<E> reportOf(E element);

    Report<E> reportAt(int index);

    void reset(Iterable<? extends E> updated);

    int size();

    void close();
  }

  @NotThreadSafe
  interface ReportedMap<K, V> extends Iterable<Map.Entry<K, V>>, AutoCloseable {
    Report<V> put(K key, V value);

    boolean remove(K key);

    V get(K key);

    Report<V> reportBy(K key);

    int size();

    @Override
    void close();
  }

  static Reporter root(Clock clock, ReportSink sink, IdSupplier idSupplier) {
    return newReporter(clock, sink, idSupplier, IdSupplier.ROOT_ID);
  }

  private static <V> Report<V> newReport(
      Clock clock, ReportSink sink, IdSupplier idSupplier, long parent, String label, V value, boolean unique) {
    var id = idSupplier.newId(parent);
    return new Report<>() {
      V v = value;
      Reporter reporter;

      {
        sink.report(toEntry(unique ? ReportEntry.Kind.Q : ReportEntry.Kind.V));
      }

      @Override
      public long id() {
        return id;
      }

      @Override
      public long parent() {
        return parent;
      }

      @Override
      public String label() {
        return label;
      }

      @Override
      public V get() {
        return v;
      }

      @Override
      public void update(V value) {
        v = value;
        sink.report(toEntry(ReportEntry.Kind.V));
      }

      @Override
      public void close() {
        sink.report(toEntry(ReportEntry.Kind.D));
      }

      @Override
      public Reporter reportUnder() {
        return reporter == null
            ? reporter = newReporter(clock, sink, idSupplier, id)
            : reporter;
      }

      @Override
      public String toString() {
        return toEntry(ReportEntry.Kind.V).toString()
            .replaceAll("^" + ReportEntry.class.getSimpleName(), Report.class.getSimpleName());
      }

      private ReportEntry toEntry(ReportEntry.Kind kind) {
        return ReportEntry.of(id, parent, kind, label, v, clock.instant());
      }
    };
  }

  private static Reporter newReporter(Clock clock, ReportSink sink, IdSupplier idSupplier, long parent) {
    return new Reporter() {
      @Override
      public Labeled as(String label) {
        return new Labeled() {
          @Override
          public <V> Report<V> put(V value) {
            return Reporter.newReport(clock, sink, idSupplier, parent, label, value, false);
          }

          @Override
          public <V> Report<V> unique(V value) {
            return Reporter.newReport(clock, sink, idSupplier, parent, label, value, true);
          }

          @Override
          public <E> ReportedSet<E> reportSet() {
            return reportSet(Integer.MAX_VALUE);
          }

          @Override
          public <E> ReportedSet<E> reportSet(Integer limit) {
            final LinkedHashMap<E, Report<E>> byValue = new LinkedHashMap<>() {
              @Override
              protected boolean removeEldestEntry(Map.Entry<E, Report<E>> eldest) {
                boolean remove = limit > 0 && limit < Integer.MAX_VALUE && size() > limit;
                if (remove) eldest.getValue().close();
                return remove;
              }
            };
            return new ReportedSet<>() {
              @Override
              public Integer limit() {
                return limit;
              }

              @Override
              public String label() {
                return label;
              }

              @Override
              public boolean add(E value) {
                var report = byValue.get(value);
                if (report != null) return false;
                byValue.put(value, Reporter.newReport(clock, sink, idSupplier, parent, label, value, false));
                return true;
              }

              @Override
              public boolean remove(E value) {
                var report = byValue.remove(value);
                if (report == null) return false;
                report.close();
                return true;
              }

              @Override
              public void removeAt(int index) {
                int i = 0;
                for (var it = byValue.entrySet().iterator(); it.hasNext(); ) {
                  var entry = it.next();
                  if (i++ == index) {
                    entry.getValue().close();
                    it.remove();
                    return;
                  }
                }
                throw new IndexOutOfBoundsException("No element with index " + index);
              }

              @Override
              public E get(int index) {
                return Iterables.get(byValue.values(), index).get();
              }

              @Override
              public Report<E> put(E value) {
                byValue.computeIfAbsent(value,
                    e -> Reporter.newReport(clock, sink, idSupplier, parent, label, value, false));
                return byValue.get(value);
              }

              @Override
              public @Nullable
              Report<E> reportOf(E element) {
                return byValue.get(element);
              }

              @Override
              public Report<E> reportAt(int index) {
                int i = 0;
                for (Map.Entry<E, Report<E>> entry : byValue.entrySet()) {
                  if (i++ == index) {
                    return entry.getValue();
                  }
                }
                throw new IndexOutOfBoundsException("No element with index " + index);
              }

              @Override
              public void reset(Iterable<? extends E> updated) {
                var retained = new HashSet<E>();
                for (E e : updated) {
                  add(e);
                  retained.add(e);
                }
                for (var it = byValue.entrySet().iterator(); it.hasNext(); ) {
                  var entry = it.next();
                  if (!retained.contains(entry.getKey())) {
                    entry.getValue().close();
                    it.remove();
                  }
                }
              }

              @Override
              public int size() {
                return byValue.size();
              }

              @Override
              public void close() {
                byValue.values().forEach(Report::close);
                byValue.clear();
              }

              @Override
              public Iterator<E> iterator() {
                return Collections.unmodifiableCollection(byValue.keySet()).iterator();
              }
            };
          }

          @Override
          public <K, V> ReportedMap<K, V> reportMap() {
            return new ReportedLinkedMap<>(clock, sink, idSupplier, parent, label);
          }

          @Override
          public String toString() {
            return Reporter.class.getSimpleName() + "." + Labeled.class.getSimpleName()
                + "(parent=" + parent + ", label=" + label + ")";
          }
        };
      }

      @Override
      public <V> ReportedMap<String, V> reportMap() {
        return new ReportedLinkedMap<>(clock, sink, idSupplier, parent);
      }

      @Override
      public String toString() {
        return Reporter.class.getSimpleName() + "(parent=" + parent + ")";
      }
    };
  }

  interface IdSupplier {
    long ROOT_ID = 0;

    long newId(long parent);
  }

  static IdSupplier randomIds() {
    var random = new Random();
    return parent -> {
      for (int i = 0; i < 100; i++) {
        var next = random.nextLong();
        if (next != 0) return next;
      }
      throw new AssertionError("should have returned non-zero id in <=100 attempt");
    };
  }

  class ReportedLinkedMap<K, V> implements ReportedMap<K, V> {

    private final LinkedHashMap<K, Report<V>> byKey = new LinkedHashMap<>();

    private final Clock clock;
    private final ReportSink sink;
    private final IdSupplier idSupplier;
    private final long parent;
    private final String label;

    public ReportedLinkedMap(Clock clock, ReportSink sink, IdSupplier idSupplier, long parent, String label) {
      this.clock = clock;
      this.sink = sink;
      this.idSupplier = idSupplier;
      this.parent = parent;
      this.label = label;
    }

    public ReportedLinkedMap(Clock clock, ReportSink sink, IdSupplier idSupplier, long parent) {
      this(clock, sink, idSupplier, parent, null);
    }

    @Override
    public Report<V> put(K key, V value) {
      var report = byKey.get(key);
      if (report != null) {
        if (!report.get().equals(value)) {
          report.update(value);
        }
        return report;
      } else {
        Report<V> newReport = Reporter.newReport(
            clock, sink, idSupplier, parent, label != null ? label : key.toString(), value, false);
        byKey.put(key, newReport);
        return newReport;
      }
    }

    @Override
    public boolean remove(K key) {
      var r = byKey.remove(key);
      if (r != null) {
        r.close();
        return true;
      }
      return false;
    }

    @Override
    public @Nullable
    V get(K key) {
      var r = byKey.get(key);
      return r == null ? null : r.get();
    }

    @Override
    public @Nullable
    Report<V> reportBy(K key) {
      return byKey.get(key);
    }

    @Override
    public int size() {
      return byKey.size();
    }

    @Override
    public void close() {
      byKey.values().forEach(Report::close);
      byKey.clear();
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
      return Iterators.transform(
          Collections.unmodifiableCollection(byKey.entrySet()).iterator(),
          e -> Map.entry(e.getKey(), e.getValue().get()));
    }
  }
}
