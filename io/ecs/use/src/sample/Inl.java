package sample;

import org.immutables.data.Data;
import org.immutables.value.Value;

@Data
@Value.Immutable(builder = false)
@Data.Inline
public interface Inl {
  @Value.Parameter String value();
  static Inl of(String value) {
    return ImmutableInl.of(value);
  }
}
