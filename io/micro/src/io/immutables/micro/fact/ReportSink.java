package io.immutables.micro.fact;

import java.util.Collections;

public interface ReportSink {
  void report(Iterable<ReportEntry> entries);

  default void report(ReportEntry entry) {
    report(Collections.singleton(entry));
  }
}
