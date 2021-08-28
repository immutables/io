package io.immutables.micro.wiring;

import io.immutables.codec.Codec;
import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.codec.Pipe;
import io.immutables.micro.Systems.Shared;
import okio.Buffer;
import okio.Okio;
import io.immutables.micro.ExceptionSink;
import io.immutables.micro.Origin;
import io.immutables.micro.Systems;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;

final class LoadSetupModule extends AbstractModule {
  @Singleton
  @ProvidesIntoSet
  public SetupLoader.PlaceholderResolver systemEnvSetupContext() {
    return key -> Optional.ofNullable(System.getenv(key));
  }

  @Provides
  @Singleton
  public @Systems SharedConf conf(OkJson json, ExceptionSink sink, Set<SetupLoader.PlaceholderResolver> contexts) {
    @Nullable String filename = System.getenv(ENV_FILE);

    Origin filenameOrigin = new Origin.Builder()
        .resource("env:" + ENV_FILE)
        .notAvailable(filename == null)
        .isFallback(filename == null)
        .fallbackInfo("defaults to ./" + DEFAULT_FILE)
        .build();

    // obviously, current working directory will be the default to resolve relative name
    File file = new File(filename != null ? filename : DEFAULT_FILE);

    Origin.Builder setupOrigin = new Origin.Builder()
        .descends(filenameOrigin)
        .resource(file.toURI().toString())
        .fallbackInfo("{}");

    if (file.exists()) {
      // don't check for .canRead() etc, it will show up as exception and it's fine
      try {
        var reader = JsonReader.of(Okio.buffer(Okio.source(file)));
        reader.setLenient(true);
        Buffer out = new Buffer();

        var writer = JsonWriter.of(out);
        Pipe.onValue(
            new EnvironmentIn(OkJson.in(reader), contexts),
            OkJson.out(writer));

        writer.flush();
        return new SharedConf(setupOrigin.build(), out.readUtf8());
      } catch (Exception ex) {
        setupOrigin.exception(sink.consumed(ex));
      }
    }

    return new SharedConf(setupOrigin
        .notAvailable(true)
        .isFallback(true)
        .build(), "{}");
  }

  private static final class SectionDecoder<C> extends Codec<C> {
    private final String section;
    private final Codec<C> codec;

    SectionDecoder(String section, Codec<C> codec) {
      this.section = section;
      this.codec = codec;
    }

    @Override public @Nullable C decode(In in) throws IOException {
      FieldIndex fields = Codec.arbitraryFields();
      @Nullable C value = null;
      in.beginStruct(fields);
      while (in.hasNext()) {
        if (value != null) {
          in.skip();
          continue;
        }
        var name = fields.indexToName(in.takeField());
        if (section.contentEquals(name)) {
          value = codec.decode(in);
        } else {
          in.skip();
        }
      }
      in.endStruct();
      return value;
    }

    @Override public void encode(Out out, C instance) throws IOException {}
  }

  @Provides
  public @Shared SetupLoader sharedSetupLoader(SetupLoader setupLoader) {
    return setupLoader;
  }

  @Provides
  @Singleton
  public SetupLoader loader(@Systems SharedConf conf, OkJson json, ExceptionSink exceptionSink) {
    return new SetupLoader() {
      @Override
      public <C> C load(Class<? extends C> type, String section, Supplier<C> fallback) {
        Origin.Builder origin = new Origin.Builder()
            .descends(conf.origin.descends())
            .resource(conf.origin.resource())
            .innerPath("$." + section);

        Codec<C> codec = json.get(type);
        var decoder = new SectionDecoder<>(section, codec);
        var in = OkJson.in(JsonReader.of(new Buffer().writeUtf8(conf.content)));
        boolean exception = false;

        try {
          @Nullable C value = decoder.decode(in);
          json.fromJson(conf.content, decoder);
          if (value != null) {
            return withOrigin(type, value, origin.build());
          }
        } catch (Exception ex) {
          origin.exception(exceptionSink.consumed(ex));
          exception = true;
        }
        // fallbackInfo would be either lambda.toString() hinting to who supplying a value
        // or proper toString(), we're not trying to take fallback instance and print it to info
        C fallbackValue = fallback.get();
        return withOrigin(type, fallbackValue, origin
            .fallbackInfo(fallbackValue.toString())
            .isFallback(true)
            .notAvailable(exception || conf.origin.notAvailable())
            .build());
      }
    };
  }

  private static <C> C withOrigin(Class<? extends C> type, C value, Origin origin) {
    if (value instanceof Origin.Traced<?>) {
      Origin.Traced<?> traced = (Origin.Traced<?>) value;
      if (traced.origin() == Origin.unspecified()) {
        return type.cast(traced.withOrigin(origin));
      }
    }
    return value;
  }

  static final class SharedConf implements Origin.Traced<SharedConf> {
    final String content;
    final Origin origin;

    SharedConf(Origin origin, String content) {
      this.content = content;
      this.origin = origin;
    }

    @Override public Origin origin() {
      return origin;
    }

    @Override public SharedConf withOrigin(Origin origin) {
      return new SharedConf(origin, content);
    }
  }

  private static final class EnvironmentIn extends Codecs.ForwardingIn {
    private final Codec.In in;
    private final Set<SetupLoader.PlaceholderResolver> resolvers;

    EnvironmentIn(Codec.In in, Set<SetupLoader.PlaceholderResolver> resolvers) {
      this.in = in;
      this.resolvers = resolvers;
    }

    @Override
    protected Codec.In delegate() {
      return in;
    }

    @Override
    public CharSequence takeString() throws IOException {
      return transform(super.takeString());
    }

    private String transform(CharSequence string) {
      StringBuilder result = new StringBuilder();
      Matcher matcher = PLACEHOLDER_PATTERN.matcher(string);
      boolean found = matcher.find();
      if (!found) return string.toString();
      do {
        String key = matcher.group(1);
        String value = getValue(key, matcher.group());
        matcher.appendReplacement(result, value.replace("$", "\\$"));
      } while (matcher.find());

      matcher.appendTail(result);
      return result.toString();
    }

    private String getValue(String key, String defaults) {
      return resolvers.stream()
          .flatMap(resolver -> resolver.get(key).stream())
          .findFirst()
          .orElse(defaults);
    }
  }

  private static final String ENV_FILE = "MICRO_SETUP";
  private static final String DEFAULT_FILE = "micro.setup.local";
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_.]+?)}");
}
