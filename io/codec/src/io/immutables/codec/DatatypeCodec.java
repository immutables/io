package io.immutables.codec;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import io.immutables.Nullable;
import io.immutables.collect.Vect;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import okio.Buffer;
import org.immutables.data.Datatype;
import org.immutables.data.Datatype.Builder;
import org.immutables.data.Datatype.Feature;
import org.immutables.data.Datatype.Violation;

@SuppressWarnings("unchecked")
final class DatatypeCaseCodec<T> extends Codec<T> {
	private final Map<String, DatatypeCodec<Object>> map;

	DatatypeCaseCodec(Datatype<T> meta, Resolver lookup) {
		assert !meta.cases().isEmpty();
		this.map = ImmutableMap.copyOf(meta.cases()
				.stream()
				.map(d -> new DatatypeCodec<>((Datatype<Object>) d, lookup, true))
				.collect(Collectors.toMap(t -> t.meta.name(), Function.identity())));
	}

	@Override
	public T decode(In in) throws IOException {
		Buffer buffer = new Buffer();
		JsonWriter w = JsonWriter.of(buffer);
		Out out = OkJson.out(w);

		@Nullable String discriminator = null;

		FieldIndex fields = Codec.arbitraryFields();
		in.beginStruct(fields);
		out.beginStruct(fields);
		while (in.hasNext()) {
			@Field int f = in.takeField();
			if (DatatypeCodec.CASE_DISCRIMINATOR.contentEquals(fields.indexToName(f))) {
				// read as discriminator case and skip to next fields
				discriminator = in.takeString().toString();
				continue;
			}
			out.putField(f);
			Pipe.onValue(in, out);
		}
		out.endStruct();
		in.endStruct();
		w.close();

		if (discriminator != null) {
			DatatypeCodec<Object> codec = map.get(discriminator);
			assert codec != null;
			In bufferedInput = OkJson.in(JsonReader.of(buffer));
			return (T) codec.decode(bufferedInput);
		}
		in.unexpected("Cannot associate codec, no @case (one of " + map.keySet() + ") is found");
		return null; // TODO not sure, overall error handling
	}

	@Override
	public void encode(Out out, T instance) throws IOException {
		for (DatatypeCodec<Object> d : map.values()) {
			if (d.meta.type().getRawType().isInstance(instance)) {
				d.encode(out, instance);
				return;
			}
		}
		out.unexpected("Cannot associate @case (one of " + map.keySet() + ") for instance " + instance);
	}
}

@SuppressWarnings("unchecked")
final class DatatypeCodec<T> extends Codec<T> {
	static final String CASE_DISCRIMINATOR = "@case";
	static final String CASE_INLINE_VALUE = "@value";
	final Datatype<T> meta;
	private final Feature<T, Object>[] features;
	private final Codec<Object>[] codecs;
	private final FieldIndex mapper;
	private final boolean asCase;
	private final @Nullable Feature<T, Object> inlineFeature;
	private final CaseFormat fieldFormat;

	DatatypeCodec(Datatype<T> meta, Resolver lookup, boolean asCase) {
		this.meta = meta;
		this.asCase = asCase;
		fieldFormat = fieldFormatOf(meta);
		features = collectFeatures(meta);
		codecs = collectCodecs(lookup, meta, features);
		mapper = indexFields(features, asCase);
		this.inlineFeature = findInlineFeature(meta);
	}

	private static CaseFormat fieldFormatOf(Datatype<?> meta) {
		var annotation = meta.type().getRawType().getAnnotation(FieldFormat.class);
		return annotation != null ? annotation.value() : CaseFormat.LOWER_CAMEL;
	}

	boolean isInline() {
		return inlineFeature != null;
	}

	private static @Nullable <T> Feature<T, Object> findInlineFeature(Datatype<T> meta) {
		if (meta.isInline()) {
			// Current support for a single inline field
			// no qualifier and only if everything else matches
			var in = Vect.from(meta.features()).filter(f -> f.supportsInput() && !f.omittableOnInput());
			var out = Vect.from(meta.features()).filter(f -> f.supportsOutput() && !f.ignorableOnOutput());
			if (in.size() == 1 && out.size() == 1 && in.first() == out.first()) {
				return (Feature<T, Object>) in.first();
			}
		}
		return null;
	}

	private Feature<T, Object>[] collectFeatures(Datatype<T> meta) {
		Feature<T, Object>[] features = meta.features().toArray(new Feature[0]);
		Map<String, Integer> nameToIndex = new HashMap<>();
		for (int i = 0; i < features.length; i++) {
			@Nullable Integer old = nameToIndex.put(features[i].name(), i);
			if (old != null) throw new IllegalStateException(
					"metadata invariant broken. Features should differ by name or being the same instance");
		}
		return features;
	}

	private Codec<Object>[] collectCodecs(Resolver lookup, Datatype<T> meta, Feature<T, ?>[] features) {
		Class<? super T> rawType = meta.type().getRawType();
		Codec<Object>[] codecs = new Codec[features.length];
		for (int i = 0; i < features.length; i++) {
			Feature<T, ?> f = features[i];
			// TODO how to extract qualifier, should be part of Feature API?
			@Nullable Annotation qualifier = findQualifier(rawType, f);
			Codec<Object> c = (Codec<Object>) lookup.get(f.type(), qualifier);
			if (f.nullable()) {
				c = c.toNullable();
			}
			codecs[i] = c;
		}
		return codecs;
	}

	private @Nullable Annotation findQualifier(Class<? super T> rawType, Feature<T, ?> f) {
		for (Method method : rawType.getDeclaredMethods()) {
			if (method.getParameterCount() == 0 && method.getName().equals(f.name())) {
				return Codecs.findQualifier(method);
			}
		}
		return null;
	}

	private FieldIndex indexFields(Feature<T, ?>[] features, boolean asCase) {
		String[] knownNames = new String[features.length + (asCase ? 2 : 0)];
		for (int i = 0; i < features.length; i++) {
			var name = features[i].name();
			if (fieldFormat != CaseFormat.LOWER_CAMEL) {
				name = CaseFormat.LOWER_CAMEL.to(fieldFormat, name);
			}
			knownNames[i] = name;
		}
		if (asCase) {
			knownNames[features.length] = CASE_DISCRIMINATOR;
			knownNames[features.length + 1] = CASE_INLINE_VALUE;
		}
		return knownFields(knownNames);
	}

	@Override
	public T decode(In in) throws IOException {
		if (isInline() && in.peek() != At.STRUCT) {
			Object value = codecs[inlineFeature.index()].decode(in);
			Builder<T> builder = meta.builder();
			builder.set(inlineFeature, value);
			return buildVerified(in, builder);
		}

		in.beginStruct(mapper);
		Builder<T> builder = meta.builder();
		while (in.hasNext()) {
			@Field int i = in.takeField();
			if (i >= 0) {
				if (i >= features.length) {
					// FIXME unknown field (auto-created by field index)
					// also for @case discriminator etc
					in.skip();
				} else {
					Feature<T, Object> f = features[i];
					if (f.supportsInput()) {
						Object value = codecs[i].decode(in);
						builder.set(f, value);
					} else {
						in.unexpected("Non-writable field: " + mapper.indexToName(i));
						in.skip(); // TODO being strict or non strict about unknown fields
					}
				}
			} else {
				in.unexpected("Unknown field: " + mapper.indexToName(i));
				in.skip(); // FIXME being strict or non strict about unknown fields
			}
		}
		in.endStruct();
		return buildVerified(in, builder);
	}

	public T buildVerified(In in, Builder<T> builder) throws IOException {
		List<Violation> violations = builder.verify();
		if (!violations.isEmpty()) {
			// TODO Think this through
			for (Violation v : violations) {
				in.unexpected(v.toString());
			}
		}
		return builder.build();
	}

	@Override
	public void encode(Out out, T instance) throws IOException {
		if (asCase) {
			out.beginStruct(mapper);
			out.putField(features.length);
			out.putString(meta.name());
			if (isInline()) {
				out.putField(features.length + 1);
				encodeInline(out, instance);
			} else {
				encodeFields(out, instance);
			}
			out.endStruct();
		} else if (isInline()) {
			encodeInline(out, instance);
		} else {
			out.beginStruct(mapper);
			encodeFields(out, instance);
			out.endStruct();
		}
	}

	public void encodeInline(Out out, T instance) throws IOException {
		Object value = meta.get(inlineFeature, instance);
		codecs[inlineFeature.index()].encode(out, value);
	}

	public void encodeFields(Out out, T instance) throws IOException {
		for (int i = 0; i < features.length; i++) {
			Feature<T, Object> f = features[i];
			if (f.supportsOutput() && !f.ignorableOnOutput()) {
				out.putField(i);
				codecs[i].encode(out, meta.get(f, instance));
			}
		}
	}
}
