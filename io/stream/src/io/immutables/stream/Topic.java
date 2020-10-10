package io.immutables.stream;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Immutable(builder = false)
public abstract class Topic {

  @Parameter
  public abstract String value();

  @Override
  public String toString() {
    return value();
  }

  public static Topic of(String name) {
    return ImmutableTopic.of(name);
  }
}
