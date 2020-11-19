package io.immutables.micro.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/**
 * Module that receives main arguments during construction and exposes them as [immutable] {@link List} of strings
 * annotated with {@code @MainArguments} inside injector.
 */
public final class ExposeMainArguments extends AbstractModule {
  private final ImmutableList<String> arguments;

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  public @interface MainArguments {}

  public ExposeMainArguments(String... arguments) {
    this.arguments = ImmutableList.copyOf(arguments);
  }

  @Provides
  @Singleton
  public @MainArguments List<String> arguments() {
    return arguments;
  }
}
