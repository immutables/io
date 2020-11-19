package io.immutables.micro.wiring;

import io.immutables.codec.Codec;
import io.immutables.codec.OkJson;
import io.immutables.codec.Resolver;
import io.immutables.regres.Jsonbs;
import io.immutables.micro.CodecsFactory;
import io.immutables.micro.Systems;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

/**
 * JSON module provides configured {@link OkJson} object. Type adapter factories can be contributed on the classpath
 * (META-INF services) or by contributing {@code @ProvidesIntoSet TypeAdapterFactory}.
 */
public final class JsonModule implements Module {

  private final Consumer<OkJson.Setup> jsonConfigurer;

  public JsonModule(Consumer<OkJson.Setup> jsonConfigurer) {
    this.jsonConfigurer = jsonConfigurer;
  }

  public JsonModule() {
    this(c -> c.indent("  "));
  }

  interface CodecConfigurer {
    void configure(CodecFactories factories);
  }

  interface CodecFactories {
    CodecFactories add(Codec.Factory factory);
    CodecFactories add(Codec.Factory factory, @Nullable Annotation qualifier, int priority);
  }

  @Override
  public void configure(Binder binder) {
    // Acts as declaration, when no contributions defined, injector will not blow up when Set requested
    Multibinder.newSetBinder(binder, CodecConfigurer.class);
  }

  @ProvidesIntoSet
  public CodecConfigurer jsonbString() {
    return f -> f.add(new OkJson.JsonStringFactory(), Jsonbs.of(), 0);
  }

  @ProvidesIntoSet
  public CodecConfigurer codecs() {
    return f -> f.add(new CodecsFactory());
  }

  @Provides
  public @Systems.Shared OkJson sharedOkJson(OkJson okJson) {
    return okJson;
  }

  @Provides
  public Resolver codecResolver(OkJson okJson) {
    return okJson;
  }

  @Provides
  public @Systems.Shared Resolver sharedCodecResolver(OkJson okJson) {
    return okJson;
  }

  @Provides
  @Singleton
  public OkJson okJson(Set<CodecConfigurer> configurers) {
    return OkJson.configure(setup -> {
      var factories = new CodecFactories() {
        @Override
        public CodecFactories add(Codec.Factory factory) {
          setup.add(factory);
          return this;
        }

        @Override
        public CodecFactories add(Codec.Factory factory, @Nullable Annotation qualifier, int priority) {
          setup.add(factory, qualifier, priority);
          return this;
        }
      };

      jsonConfigurer.accept(setup);

      for (var c : configurers) {
        c.configure(factories);
      }
    });
  }
}
