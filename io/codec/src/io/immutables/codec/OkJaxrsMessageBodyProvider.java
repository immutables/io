package io.immutables.codec;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import com.google.common.reflect.TypeToken;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;

/**
 * Json(Codecs+Moshi) serialization provider for JAX-RS 1.0 and JAX-RS 2.0.
 */
@Provider
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.WILDCARD)
@SuppressWarnings({"resource", "unused"})
public class OkJaxrsMessageBodyProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object> {
  private final OkJson json;
  private final Set<MediaType> mediaTypes;
  private final OkStreamer streamer;
  private final ExceptionHandler exceptionHandler;

  /**
   * Creates new provider with internally configured {@link OkJson} instance,
   * and {@link MediaType#APPLICATION_JSON_TYPE application/json} media type to match.
   */
  public OkJaxrsMessageBodyProvider() {
    this(new OkJson());
  }

	public OkJaxrsMessageBodyProvider(OkJson json) {
  	this(json, Set.of(), DEFAULT_EXCEPTION_HANDLER);
	}

  /**
   * Creates new provider with flexible setup.
   */
  public OkJaxrsMessageBodyProvider(OkJson json, Set<MediaType> mediaTypes, ExceptionHandler exceptionHandler) {
    this.json = json;
    this.mediaTypes = !mediaTypes.isEmpty() ? mediaTypes : DEFAULT_JSON_MEDIA_TYPES;
		this.streamer = new OkStreamer(json);
		this.exceptionHandler = exceptionHandler;
	}

  private static Set<MediaType> mediaSetFrom(List<MediaType> mediaTypes) {
    if (mediaTypes.isEmpty()) {
      return DEFAULT_JSON_MEDIA_TYPES;
    }
    return new HashSet<MediaType>(mediaTypes);
  }

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return mediaTypes.contains(mediaType) && !UNSUPPORTED_TYPES.contains(type);
  }

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return mediaTypes.contains(mediaType) && !UNSUPPORTED_TYPES.contains(type);
  }

  @Override
  public long getSize(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(
      Object t,
      Class<?> type,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders,
      OutputStream entityStream)
      throws IOException,
        WebApplicationException {
    // Special case of unsupported type, where surrounding framework
    // may have, mistakenly, chosen this provider based on media type, but when
    // response will be streamed using StreamingOutput or is already prepared using
    // in the form of CharSequence
    if (t instanceof StreamingOutput) {
      ((StreamingOutput) t).write(entityStream);
      return;
    }
    if (t instanceof CharSequence) {
      // UTF-8 used because it should be considered default encoding for the JSON-family
      // of media types
      OutputStreamWriter writer = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8);
      writer.append((CharSequence) t);
      writer.flush();
      return;
    }
    // Standard way of handling writing using gson
    try {
      streamer.write(genericType, annotations, t, entityStream);
    } catch (IOException ex) {
      exceptionHandler.onWrite(json, ex);
      throw ex;
    }
  }

  @Override
  public Object readFrom(
      Class<Object> type,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, String> httpHeaders,
      InputStream entityStream)
      throws IOException,
        WebApplicationException {
    try {
      return streamer.read(genericType, annotations, entityStream);
    } catch (IOException ex) {
      exceptionHandler.onRead(json, ex);
      throw ex;
    }
  }

	@SuppressWarnings("unchecked")
  private static final class OkStreamer {
		private final OkJson json;

		OkStreamer(OkJson json) {
			this.json = json;
		}

		void write(Type type, Annotation[] annotations, Object object, OutputStream stream) throws IOException {
			BufferedSink sink = Okio.buffer(Okio.sink(stream));
      JsonWriter writer = JsonWriter.of(sink);
			Codec<Object> codec = getCodec(json, type, annotations);
      // we don't use ARM (try-with-resources) probably because we don't want call close, we only flush
      @Nullable Exception originalException = null;
      try {
				json.init(writer);
				codec.encode(OkJson.out(writer), object);
      } catch (IOException ex) {
				originalException = ex;
        throw ex;
      } catch (Exception ex) {
				originalException = ex;
        throw new IOException(ex);
      } finally {
				try {
					// underlying stream should not be closed, just flushing
					writer.flush();
				} catch (IOException ex) {
					if (originalException != null) {
						originalException.addSuppressed(ex);
					} else {
						throw ex;
					}
				}
      }
    }

    Object read(Type type, Annotation[] annotations, InputStream stream) throws IOException {
			BufferedSource source = Okio.buffer(Okio.source(stream));
			JsonReader reader = JsonReader.of(source);
			Codec<Object> codec = getCodec(json, type, annotations);
			// we don't use ARM (try-with-resources) probably because we don't want call close, we only flush
      try {
      	json.init(reader);
				return codec.decode(OkJson.in(reader));
      } catch (IOException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new IOException(ex);
      }
    }

		private Codec<Object> getCodec(OkJson json, Type type, Annotation[] annotations) {
			return (Codec<Object>) json.get(TypeToken.of(type), Codecs.findQualifier(annotations, type));
		}
  }

  /**
   * Implement streaming exception handler. If now exception will be thrown by handler methods,
   * original {@link IOException} will be rethrown. Note that any runtime exceptions thrown by
   * {@code OkJson} will be wrapped in {@link IOException} passed in.
   */
  public interface ExceptionHandler {
    /**
     * Handles read exception. Can throw checked {@link IOException} or any runtime exception,
     * including {@link WebApplicationException}.
     * @param json OkJson instance if JSON serializer needed to format error response.
     * @param exception thrown from within {@link OkJson}
     * @throws IOException IO exception, to be handled by JAX-RS implementation
     * @throws WebApplicationException exception which forces certain response
     * @throws RuntimeException any runtime exception to be handled by JAX-RS implementation
     */
    void onRead(OkJson json, Exception exception) throws IOException, WebApplicationException, RuntimeException;

    /**
     * Handles write exception. Can throw checked {@link IOException} or any runtime exception,
     * including {@link WebApplicationException}.
     * @param json OkJson instance if JSON serializer needed to format error response.
     * @param exception thrown from within {@link OkJson}
     * @throws IOException IO exception, to be handled by JAX-RS implementation
     * @throws WebApplicationException exception which forces certain response
     * @throws RuntimeException any runtime exception to be handled by JAX-RS implementation
     */
    void onWrite(OkJson json, Exception exception) throws IOException, WebApplicationException, RuntimeException;
  }

  private static final Set<Class<?>> UNSUPPORTED_TYPES = Set.of(
  		InputStream.class,
			Reader.class,
			OutputStream.class,
			Writer.class,
			byte[].class,
			char[].class,
			String.class,
			StreamingOutput.class,
			Response.class);

	private static final Set<MediaType> DEFAULT_JSON_MEDIA_TYPES = Set.of(
			MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_PATCH_JSON_TYPE);

  private static final ExceptionHandler DEFAULT_EXCEPTION_HANDLER = new ExceptionHandler() {
    @Override
    public void onWrite(OkJson json, Exception ex) {}

    @Override
    public void onRead(OkJson json, Exception ex) {
      StreamingOutput output = new StreamingOutput() {
        @Override
        public void write(OutputStream output) throws IOException, WebApplicationException {
					Codec<Map<String, String>> codec = json.get(new TypeToken<Map<String, String>>() {});
					JsonWriter writer = JsonWriter.of(Okio.buffer(Okio.sink(output)));
					codec.encode(OkJson.out(writer), Map.of("error", ex.getCause().getMessage()));
					writer.flush();
        }
      };
      if (ex.getCause() instanceof RuntimeException) {
        throw new WebApplicationException(
            Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(output)
                .build());
      }
    }
  };
}
