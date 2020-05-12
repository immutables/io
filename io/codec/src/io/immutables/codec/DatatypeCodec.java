package io.immutables.codec;

import io.immutables.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.immutables.data.Datatype;
import org.immutables.data.Datatype.Builder;
import org.immutables.data.Datatype.Feature;
import org.immutables.data.Datatype.Violation;

@SuppressWarnings("unchecked")
final class DatatypeCodec<T> extends Codec<T> {
	private final Datatype<T> meta;
	private final Feature<T, Object>[] features;
	private final Codec<Object>[] codecs;
	private final FieldIndex mapper;

	DatatypeCodec(Datatype<T> meta, Resolver lookup) {
		this.meta = meta;
		features = collectFeatures(meta);
		codecs = collectCodecs(lookup, features);
		mapper = indexFields(features);
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

	private Codec<Object>[] collectCodecs(Resolver lookup, Feature<T, ?>[] features) {
		Codec<Object>[] codecs = new Codec[features.length];
		for (int i = 0; i < features.length; i++) {
			Feature<T, ?> f = features[i];
			// TODO how to extract qualifier, should be part of Feature API?
			Codec<Object> c = (Codec<Object>) lookup.get(f.type());
			if (f.nullable()) {
				c = c.toNullable();
			}
			codecs[i] = c;
		}
		return codecs;
	}

	private FieldIndex indexFields(Feature<T, ?>[] features) {
		String[] knownNames = new String[features.length];
		for (int i = 0; i < features.length; i++) {
			knownNames[i] = features[i].name();
		}
		return knownFields(knownNames);
	}

	@Override
	public T decode(In in) throws IOException {
		in.beginStruct(mapper);
		Builder<T> builder = meta.builder();
		while (in.hasNext()) {
			@Field int i = in.takeField();
			if (i >= 0) {
				Feature<T, Object> f = features[i];
 				if (f.supportsInput()) {
					Object value = codecs[i].decode(in);
					builder.set(f, value);
				} else {
					in.unexpected("Non-writable field: " + mapper.indexToName(i));
					in.skip(); // TODO being strict or non strict about unknown fields
				}
			} else {
				in.unexpected("Unknown field: " + mapper.indexToName(i));
				in.skip(); // TODO being strict or non strict about unknown fields
			}
		}
		in.endStruct();
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
		out.beginStruct(mapper);
		for (int i = 0; i < features.length; i++) {
			Feature<T, Object> f = features[i];
			if (f.supportsOutput() && !f.ignorableOnOutput()) {
				out.putField(i);
				codecs[i].encode(out, meta.get(f, instance));
			}
		}
		out.endStruct();
	}
}
