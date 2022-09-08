package org.immutables.mongo.fixture.adapters;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;

public final class Props {
  @JsonIgnore
  private final Map<String, Object> any = new HashMap<>();

  @JsonAnyGetter
  public Map<String, Object> getAny() {
    return any;
  }

  @JsonAnySetter
  public void put(String field, Object value) {
    any.put(field, value);
  }

  @Override
  public String toString() {
    return "Any" + any;
  }
}
