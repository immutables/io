package sample;

import a.KeyVal;
import a.ecs.KeyValServicelet;
import io.immutables.micro.tester.ServiceletTester;
import io.immutables.micro.tester.TesterFacets;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static io.immutables.that.Assert.that;

@RunWith(ServiceletTester.class)
public class GenTest {
  public static void init(TesterFacets t) {
    t.servicelets(KeyValServicelet.KEY_VAL)
        .http(h -> h.requireAll());
  }

  @Inject KeyVal keyVal;

  @Test public void test() {

    keyVal.put("X", "Y");
    keyVal.put("X", "Z");

    String v = keyVal.get("X");

    that(v).is("Z");
  }
}
