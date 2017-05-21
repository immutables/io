package io.immutables.jaeger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ServiceLoader;
import org.immutables.gson.stream.JsonGeneratorWriter;
import org.immutables.gson.stream.JsonParserReader;

public final class Confs {
	private Confs() {}

	// TODO YAML seems to not properly gives array index for json path construction
	static final YAMLFactory YAML = new YAMLFactory();

	static final JsonFactory JSON = new JsonFactory()
			.enable(Feature.ALLOW_COMMENTS)
			.enable(Feature.ALLOW_SINGLE_QUOTES)
			.enable(Feature.ALLOW_UNQUOTED_FIELD_NAMES);

	private static final Gson GSON = configureGson();

	public static Gson gson() {
		return GSON;
	}

	private static Gson configureGson() {
		GsonBuilder builder = new GsonBuilder()
				.setPrettyPrinting()
				.serializeNulls()
				.setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
				.disableHtmlEscaping();

		for (TypeAdapterFactory factory : ServiceLoader.load(TypeAdapterFactory.class)) {
			builder.registerTypeAdapterFactory(factory);
		}
		builder.registerTypeAdapterFactory(new GsonAdaptersSwagger());
		return builder.create();
	}

	public static String toJson(Object deployment) {
		return GSON.toJson(deployment);
	}

	public static String toYaml(Object object) {
		return write(YAML, object);
	}

	static Object read(
			JsonFactory jsonFactory,
			String path,
			String source,
			Type type) {

		try {
			try (JsonParser parser = jsonFactory.createParser(source);
					JsonParserReader in = new JsonParserReader(parser)) {
				in.setLenient(true);

				try {
					return GSON.getAdapter(TypeToken.get(type)).read(in);
				} catch (Exception ex) {
					JsonLocation location = ex instanceof JsonProcessingException
							? ((JsonProcessingException) ex).getLocation()
							: parser.getCurrentLocation();

					String exceptionMessage =
							ex instanceof JsonProcessingException
									? ((JsonProcessingException) ex).getOriginalMessage()
									: ex.getMessage();

					String message = "Cannot read " + path + "\n"
							+ exceptionMessage
							+ "\n\t" + Joiner.on("\n\t").join(in.getLocationInfo());

					ConfigException configException = new ConfigException(message, ex);
					configException.setStackTrace(new StackTraceElement[] {configException.getStackTrace()[0]});
					throw configException;
				}
			}
		} catch (IOException exception) {
			throw new ConfigException("Cannot read " + path, exception);
		}
	}

	// safe resource: in memory resources gets garbage collected
	// was problems with closing yaml
	@SuppressWarnings("resource")
	private static String write(JsonFactory jsonFactory, Object object) {
		try {
			StringWriter writer = new StringWriter();
			JsonGenerator generator = jsonFactory.createGenerator(writer);
			JsonGeneratorWriter out = new JsonGeneratorWriter(generator);
			out.setSerializeNulls(false);

			try {
				// safe unchecked: checked at runtime using type tokens
				@SuppressWarnings("unchecked") TypeToken<Object> type =
						(TypeToken<Object>) TypeToken.get(object.getClass());

				GSON.getAdapter(type).write(out, object);
				return writer.toString();
			} catch (Exception ex) {
				String message = "Cannot write " + object + "\n"
						+ ex.getMessage()
						+ "\n\t" + Joiner.on("\n\t").join(out.getLocationInfo());

				ConfigException configException = new ConfigException(message, ex);
				configException.setStackTrace(new StackTraceElement[] {configException.getStackTrace()[0]});
				throw configException;
			}
		} catch (IOException ioe) {
			throw new ConfigException("Cannot write  " + object, ioe);
		}
	}

	public static class ConfigException extends RuntimeException {
		ConfigException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
