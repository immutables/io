package io.immutables.micro.fact;

import org.immutables.data.Data;
import org.immutables.value.Value;
import java.util.List;

@Data
@Value.Immutable(builder = false)
public abstract class OutputNode {
  public abstract @Value.Parameter long id();
  public abstract @Value.Parameter String label();
  public abstract @Value.Parameter Object value();
  public abstract @Value.Parameter List<OutputNode> children();
  public static OutputNode of(long id, String label, Object value, List<OutputNode> children) {
    return ImmutableOutputNode.of(id, label, value, children);
  }
}
