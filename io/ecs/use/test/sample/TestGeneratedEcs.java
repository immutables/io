package sample;

import a.Coord;
import a.Obj;
import a.ObjCoord;
import a.ecs.AServicelet;
import io.immutables.micro.tester.ServiceletTester;
import io.immutables.micro.tester.TesterFacets;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static io.immutables.that.Assert.that;

@RunWith(ServiceletTester.class)
public class TestGeneratedEcs {
  public static void init(TesterFacets t) {
    t.servicelets(AServicelet.A)
        .http(h -> h.requireAll());
  }

  @Inject ObjCoord objCoord;

  @Test public void test() {
		objCoord.put(Obj.of("X"), "1", ObjCoord.Input.of(Coord.of(1, 1, 1)));
		objCoord.put(Obj.of("X"), "2", ObjCoord.Input.of(Coord.of(2, 2, 2)));
    that(objCoord.get(Obj.of("X"), "1").coord()).equalTo(Coord.of(1, 1, 1));
		that(objCoord.get(Obj.of("X"), "2").coord()).equalTo(Coord.of(2, 2, 2));
  }
}
