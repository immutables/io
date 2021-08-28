package io.immutables.micro.fact;

import org.immutables.data.Data;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import java.time.Instant;

@Immutable(builder = false)
@Data
public abstract class ReportEntry {
  public abstract @Parameter long id();
  public abstract @Parameter long parent();
  public abstract @Parameter Kind kind();
  public abstract @Parameter String label();
  public abstract @Parameter Object value();
  public abstract @Parameter Instant timestamp();

  public enum Kind {
    /** delete/discontinue fact/value */
    D,
    /** introduce value */
    V,
    /** update value */
    U,
    /**
     * introduce unique value, discontinuing (if possible) any other reported entry under the same
     * parent having the same key:value
     */
    Q,
    /** element/entry in collection */
    E,
    /** increment */
    I
  }

  public static ReportEntry of(long id, long parent, Kind kind, String label, Object value, Instant timestamp) {
    return ImmutableReportEntry.of(id, parent, kind, label, value, timestamp);
  }
}
