package io.immutables.micro;

import io.immutables.codec.Codec;
import io.immutables.codec.Resolver;
import java.io.IOException;
import javax.annotation.Nullable;
import com.google.common.net.HostAndPort;
import com.google.common.reflect.TypeToken;
import com.google.inject.Key;

public final class CodecsFactory implements Codec.Factory {
  @SuppressWarnings("unchecked")
  @Override
  public @Nullable <T> Codec<T> get(Resolver lookup, TypeToken<T> type) {
    Class<?> t = type.getRawType();
    if (t == Key.class) return (Codec<T>) keyCodec;
    if (t == HostAndPort.class) return (Codec<T>) hostPortCodec;
    if (t == DynamicOutput.class) return (Codec<T>) new DynamicOutputCodec(lookup);
    return null;
  }

  private static final Codec<Key<?>> keyCodec = new Codec<>() {
    @Override
    public Key<?> decode(In in) throws IOException {
      throw new IOException("Keys should not be decoded, use Manifest.Reference instead");
    }

    @Override
    public void encode(Out out, Key<?> key) throws IOException {
      throw new IOException("Keys should not be encoded, use Manifest.Reference instead");
    }
  };

  private static final Codec<HostAndPort> hostPortCodec = new Codec<>() {
    @Override
    public HostAndPort decode(In in) throws IOException {
      return HostAndPort.fromString(in.takeString().toString());
    }

    @Override
    public void encode(Out out, HostAndPort host) throws IOException {
      out.putString(host.toString());
    }
  };

  private static final class DynamicOutputCodec extends Codec<DynamicOutput> {
    private final Resolver resolver;

    DynamicOutputCodec(Resolver resolver) {
      this.resolver = resolver;
    }

    @Override
    public DynamicOutput decode(In in) throws IOException {
      in.unexpected("Cannot decode DynamicOutput instance");
      return null; // OK? or exception
    }

    @SuppressWarnings("unchecked")
    @Override
    public void encode(Out out, DynamicOutput value) throws IOException {
      if (value.value == null) {
        out.putNull();
        return;
      }
      var type = TypeToken.of(value.value.getClass());
      ((Codec<Object>) resolver.get(type)).encode(out, value.value);
    }
  }
}
