package io.immutables.micro;

import io.immutables.codec.Codec;
import io.immutables.codec.Codecs;
import io.immutables.codec.Resolver;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    if (t == Object.class) return (Codec<T>) new DynamicObjectCodec(lookup);
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

  @Deprecated(forRemoval = true)// we have full featured Object.class codec instead
  private static final class DynamicOutputCodec extends Codec<DynamicOutput> {
    private final Codec<Object> delegate;

    DynamicOutputCodec(Resolver resolver) {
      this.delegate = resolver.get(Object.class);
    }

    @Override
    public DynamicOutput decode(In in) throws IOException {
      return DynamicOutput.wrap(delegate.decode(in));
    }

    @Override
    public void encode(Out out, DynamicOutput value) throws IOException {
      delegate.encode(out, value.value);
    }
  }

  @SuppressWarnings("unchecked")
  private static final class DynamicObjectCodec extends Codec<Object> {
    private static final TypeToken<Map<Object, Object>> mapTypeToken = new TypeToken<>() {};
    private static final TypeToken<List<Object>> listTypeToken = new TypeToken<>() {};
    private final Resolver resolver;
    private Codec<Map<Object, Object>> mapCodec;
    private Codec<List<Object>> listCodec;

    DynamicObjectCodec(Resolver resolver) {
      this.resolver = resolver;
    }

    private Codec<Map<Object, Object>> codecForStruct() {
      return mapCodec == null ? mapCodec = resolver.get(mapTypeToken) : mapCodec;
    }

    private Codec<List<Object>> codecForArray() {
      return listCodec == null ? listCodec = resolver.get(listTypeToken) : listCodec;
    }

    @Override
    public void encode(Out out, Object value) throws IOException {
      if (value == null) {
        out.putNull();
      } else if (value instanceof CharSequence) {
        out.putString(((CharSequence) value));
      } else if (value instanceof Boolean) {
        out.putBoolean((Boolean) value);
      } else if (value instanceof Long) {
        out.putLong((Long) value);
      } else if (value instanceof Integer) {
        out.putInt((Integer) value);
      } else if (value instanceof Number) {
        out.putDouble(((Number) value).doubleValue());
      } else if (value.getClass() == Object.class) {
        out.putString(value.toString());
      } else if (Collection.class.isAssignableFrom(value.getClass())) {
        codecForArray().encode(out, List.copyOf((Collection<?>) value));
      } else if (Map.class.isAssignableFrom(value.getClass())) {
        codecForStruct().encode(out, (Map<Object, Object>) value);
      } else {
        var type = TypeToken.of(value.getClass());
        var codec = ((Codec<Object>) resolver.get(type));
        if (Codecs.isUnsupported(codec)) {
          out.putString(value.toString());
        } else {
          codec.encode(out, value);
        }
      }
    }

    @Override
    public Object decode(In in) throws IOException {
      switch (in.peek()) {
      case STRING:
        return in.takeString();
      case LONG:
        return in.takeLong();
      case INT:
        return in.takeInt();
      case DOUBLE:
        return in.takeDouble();
      case BOOLEAN:
        return in.takeBoolean();
      case STRUCT:
        return codecForStruct().decode(in);
      case ARRAY:
        return codecForArray().decode(in);
      case NULL:
        in.takeNull();
        return null;
      default:
        in.skip();
        return null;
      }
    }
  }
}
