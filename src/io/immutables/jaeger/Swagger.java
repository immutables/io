package io.immutables.jaeger;

import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.immutables.gson.Gson.TypeAdapters;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Enclosing
@TypeAdapters(emptyAsNulls = true)
public interface Swagger {
	String VERSION = "2.0";

	public enum Scheme {
		http, https, ws, wss
	}

	public enum Type {
		@SerializedName("boolean")
		bool, object, array, integer, number, string;
	}

	interface ContentTypes {
		List<String> produces();
		List<String> consumes();
	}

	@Immutable
	interface RootObject extends ContentTypes {
		default @Default String swagger() {
			return VERSION;
		}
		@Nullable InfoObject info();
		@Nullable URI host();
		@Nullable String basePath();
		List<Scheme> schemes();
		Map<String, PathObject> paths();
		Map<String, SchemaObject> definitions();

		class Builder extends ImmutableSwagger.RootObject.Builder {}
	}

	@Immutable
	interface InfoObject {
		@Nullable
		String title();
		@Nullable
		String version();
		Optional<String> description();

		class Builder extends ImmutableSwagger.InfoObject.Builder {}
	}

	@Immutable
	interface PathObject {
		Optional<OperationObject> get();
		Optional<OperationObject> post();

		class Builder extends ImmutableSwagger.PathObject.Builder {}
	}

	@Immutable
	interface OperationObject extends ContentTypes {
		Optional<String> description();
		Optional<String> operationId();
		List<ParameterObject> parameters();
		Map<Integer, ResponseObject> responses();

		class Builder extends ImmutableSwagger.OperationObject.Builder {}
	}

	@Immutable
	interface ParameterObject {
		enum Kind {
			query, header, path, formData, body
		}
		String name();
		Kind in();
		Optional<String> description();
		boolean required();

		Optional<RefObject> schema();
		Optional<Type> type();
		Optional<String> format();

		class Builder extends ImmutableSwagger.ParameterObject.Builder {}
	}

	@Immutable
	interface RefObject {
		@Parameter
		@SerializedName("$ref")
		String ref();

		static RefObject of(String ref) {
			return ImmutableSwagger.RefObject.of(ref);
		}

		class Builder extends ImmutableSwagger.RefObject.Builder {}
	}

	@Immutable
	interface SchemaObject {
		Optional<Type> type();
		Optional<String> format();
		@SerializedName("enum")
		Set<String> enums();
		Set<String> required();
		Optional<RefObject> items();
		Map<String, RefObject> properties();

		class Builder extends ImmutableSwagger.SchemaObject.Builder {}
//	List<RefObject> allOf();
//	List<RefObject> anyOf();
//	List<RefObject> oneOf();
//	Optional<RefObject> not();
	}

	@Immutable
	interface ResponseObject {
		String description();
		RefObject schema();
		Map<String, SchemaObject> headers();

		class Builder extends ImmutableSwagger.ResponseObject.Builder {}
	}

	interface Formats {
		String INT32 = "int32";
		String INT64 = "int64";
		String FLOAT = "float";
		String DOUBLE = "double";
		String BYTE = "byte";
		String BINARY = "binary";
		String DATE = "date";
		String DATE_TIME = "date-time";
		String PASSWORD = "password";
	}
}
