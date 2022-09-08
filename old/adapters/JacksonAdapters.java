package org.immutables.mongo.fixture.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class JacksonAdapters implements TypeAdapterFactory {
  private final ObjectMapper mapper = new ObjectMapper();

  public JacksonAdapters() {

  }

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    if (shouldDelegate(type.getRawType())) {
      return new DelegatingAdapter<>();
    }
    return null;
  }

  private boolean shouldDelegate(Class<?> rawType) {
    // here we need to determine that type should go to Jackson
    // we can do this for all types and expect that the ones bound by Gson for top
    // level documents will just have type adapters factory preceeding
    // which will depend on the order in which factory is registered by
    // using GsonBuilder#registerTypeAdapterFactory
    return false;
  }

  private final class DelegatingAdapter<T> extends TypeAdapter<T> {

    @Override
    public T read(JsonReader in) throws IOException {
      // here we just assume this adapter will only be used for mapping BSON documents
      // if it will be used for plain JSON then more elaborate handling and integration required
      // BsonReader reader = (BsonReader) in;
      return null;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
      // TODO auto
    }
  }
}
