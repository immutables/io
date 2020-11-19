package io.immutables.micro;

import javax.annotation.Nullable;

public final class DynamicOutput {
  public final Object value;

  private DynamicOutput(Object value) {
    this.value = value;
  }

  public static DynamicOutput wrap(@Nullable Object value) {
    return new DynamicOutput(value);
  }
}
