package io.immutables.micro.wiring.jersey;

import io.immutables.codec.Codec;
import io.immutables.codec.Codecs;
import io.immutables.codec.Resolver;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.annotation.Priority;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import com.google.common.reflect.TypeToken;
import static io.immutables.codec.Codecs.stringIo;

@Priority(11)
@Provider
public class ParameterConverter implements ParamConverterProvider {
  private final Resolver resolver;

  public ParameterConverter(Resolver resolver) {
    this.resolver = resolver;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    Codec<T> codec = (Codec<T>) resolver.get(TypeToken.of(genericType), Codecs.findQualifier(annotations, rawType));
    if (Codecs.isUnsupported(codec)) {
      return new ParamConverter<T>() {
        @Override
        public T fromString(String value) {
          throw new IllegalArgumentException("Cannot convert " + genericType + " from string: \"" + value + "\"");
        }

        @Override
        public String toString(T value) {
          return String.valueOf(value);
        }
      };
    }
    return new ParamConverter<T>() {
      @Override
      public T fromString(String value) {
        try {
          var io = stringIo();
          io.putString(value);
          return codec.decode(io);
        } catch (Exception ex) {
          throw new IllegalArgumentException("Cannot convert " + genericType + " from string: \"" + value + "\"", ex);
        }
      }

      @Override
      public String toString(T value) {
        try {
          var io = stringIo();
          codec.encode(io, value);
          return io.takeString().toString();
        } catch (Exception ex) {
          throw new IllegalArgumentException("Cannot convert " + genericType + " to string: " + value + "", ex);
        }
      }
    };
  }
}
