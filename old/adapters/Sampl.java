package org.immutables.mongo.fixture.adapters;

import org.immutables.gson.Gson;
import org.immutables.mongo.Mongo;
import org.immutables.mongo.types.Id;
import org.immutables.value.Value;

@Mongo.Repository
@Value.Immutable
@Gson.TypeAdapters
public interface Sampl {
  @Mongo.Id
  Id id();

  Props props();
}
