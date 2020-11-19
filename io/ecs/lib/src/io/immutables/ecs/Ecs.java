package io.immutables.ecs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.immutables.value.Value;

/**
 * Umbrella for ecs marker annotation. Used mostly for discovery/modelling etc
 */
@Target({})
public @interface Ecs {
  @Value.Style(
      overshadowImplementation = true,
      visibility = Value.Style.ImplementationVisibility.PACKAGE,
      defaults = @Value.Immutable(builder = false))
  @interface Style {}

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Entity {}

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Slug {}

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Component {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Contract {
    Class<?> value();
  }
}
