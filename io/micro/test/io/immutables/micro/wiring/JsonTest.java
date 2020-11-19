package io.immutables.micro.wiring;

import io.immutables.codec.OkJson;
import io.immutables.micro.Launcher;
import io.immutables.that.Assert;
import java.time.Instant;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class JsonTest {
  static final JsonModule jsonModule = new JsonModule();
  static final SampleEndpoint.Val VAL = SampleEndpoint.Val.of("A", 2, Instant.EPOCH);

  @Test
  public void jsonModuleProvidesGsonWithTimeAdapters() {
    OkJson json = new Launcher()
        .add(jsonModule)
        .inject()
        .getInstance(OkJson.class);

    that(json.toJson(VAL, SampleEndpoint.Val.class)).contains("\"1970-01-01T00:00:00Z\"");

    Assert.that(json.fromJson(json.toJson(VAL, SampleEndpoint.Val.class), SampleEndpoint.Val.class)).equalTo(VAL);
  }
}
