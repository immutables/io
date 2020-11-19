package io.immutables.micro;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Systems {
  /**
   * Qualifier for platform objects to be exposed in unqualified form in servicelet modules, as each servicelet module
   * inherit parent injector with such "shared" bindings. All other platform bindings will be inaccessible from within
   * servicelet module.
   */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  public @interface Shared {}
}
