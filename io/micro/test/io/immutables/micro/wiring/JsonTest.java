package io.immutables.micro.wiring;

import io.immutables.codec.OkJson;
import io.immutables.micro.Launcher;
import io.immutables.that.Assert;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class JsonTest {
  static final JsonModule jsonModule = new JsonModule();
  static final SampleEndpoint.Val VAL = SampleEndpoint.Val.of("A", 2, Instant.EPOCH);
  final OkJson okJson = new Launcher()
      .add(jsonModule)
      .inject()
      .getInstance(OkJson.class);

  @Test
  public void jsonModuleProvidesGsonWithTimeAdapters() {
    that(okJson.toJson(VAL, SampleEndpoint.Val.class)).contains("\"1970-01-01T00:00:00Z\"");

    Assert.that(okJson.fromJson(okJson.toJson(VAL, SampleEndpoint.Val.class), SampleEndpoint.Val.class)).equalTo(VAL);
  }

  @Test
  public void dynamicObjectAdapterForStruct() {
    var codec = okJson.get(Object.class);
    var json = okJson.toJson(VAL, codec);
    var object = okJson.fromJson(json, codec);
    that(object).equalTo(Map.<String, Object>of(
        "a", VAL.a(),
        "b", (double)VAL.b(),
        "at", VAL.at().toString()));
  }

  @Test
  public void dynamicObjectAdapterForArray() {
    var codec = okJson.get(Object.class);
    var json = okJson.toJson(List.<Object>of(1.0, "a", true), codec);
    var object = okJson.fromJson(json, codec);
    that(object).equalTo(List.<Object>of(1.0, "a", true));
  }
}
