package io.immutables.codec;

import io.immutables.Nullable;
import okio.Buffer;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableMap;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import org.immutables.data.Datatype;

@SuppressWarnings("unchecked")
public final class DatatypeCaseCodec<T> extends Codec<T> {
  private final Map<String, DatatypeCodec<Object>> map;

  public DatatypeCaseCodec(Datatype<T> meta, Resolver lookup) {
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
