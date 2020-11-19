package io.immutables.micro.wiring;

import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Provides access to configuration section and hiding JSON details.
 */
interface SetupLoader {
  /**
   * Loads configuration object from configuration provided to launcher in JSON or similar mechanism. In case
   * configuration is not there or there's an exception reading config the default value will created and returned. It
   * is the caller's responsibility to handle default instance differently if it wishes to do so or throw exception from
   * supplier to complain about missing required configuration, exceptions from supplier will not be caught internally.
   * For simplicity, the implementation doesn't cache configuration objects and it's also caller's responsibility if
   * desirable to pin configuration object. Note in case unmarshalling from JSON or similar formats, the actual codecs
   * for the type in question should be registered elsewhere, either automatically (in case of generic codec) or
   * explicitly. (see {@link JsonModule})
   * @param type type of configuration section object
   * @param section field name holding configuration structure
   * @param fallback fallback/default value supplier is a must
   * @param <C> configuration object type.
   */
  <C> C load(Class<? extends C> type, String section, Supplier<C> fallback);

  static <C> Provider<C> provider(Class<? extends C> type, String section, Supplier<C> fallback) {
    return new Provider<C>() {
      @Inject
      SetupLoader loader;

      @Override
      public C get() {
        return loader.load(type, section, fallback);
      }
    };
  }

  /**
   * Resolves variables in configuration files. Variable names should be wrapped with {@code ${}} as part of
   * configuration strings. Default implementation would expose environment variables.
   */
  interface PlaceholderResolver {
    /** Get value for a specified key if available. */
    Optional<String> get(String key);
  }
}
