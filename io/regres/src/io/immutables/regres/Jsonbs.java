package io.immutables.regres;

public final class Jsonbs {
  private Jsonbs() {}

  public static Jsonb of() {
    return ImmutableJsonb.of();
  }
}
