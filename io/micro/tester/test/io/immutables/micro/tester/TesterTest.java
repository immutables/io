package io.immutables.micro.tester;

import com.google.common.collect.ImmutableMap;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.immutables.micro.DatabaseScript;
import io.immutables.micro.Facets;
import io.immutables.micro.Servicelet;
import io.immutables.regres.SqlAccessor;
import java.sql.SQLException;
import static io.immutables.that.Assert.that;

@RunWith(ServiceletTester.class)
public class TesterTest {

  public static class Motd implements MessageOfTheDay {
    private final String motd;
    @Inject
    public Motd(@Named("motd") String motd) {
      this.motd = motd;
    }

    @Override
    public String get() {
      return motd;
    }
  }

  public static class Hyper implements HypeOfMoment {
    private final MessageOfTheDay motd;
    @Inject
    public Hyper(MessageOfTheDay motd) {
      this.motd = motd;
    }

    @Override
    public String get() {
      return "!" + motd.get() + "!";
    }
  }

  public interface SomeRepo extends SqlAccessor {}

  private static final Servicelet A = new Facets("message-of-the-day")
      .http(h -> {
        h.provide(MessageOfTheDay.class).bindClass(Motd.class);
      })
      .configure(b -> {
        Names.bindProperties(b, ImmutableMap.of("motd", "FTW"));
      })
      .toServicelet();

  private static final Servicelet B = new Facets("hype-of-a-moment")
      .http(h -> {
        h.require(MessageOfTheDay.class);
        h.provide(HypeOfMoment.class).bindClass(Hyper.class);
      })
      .database(d -> {
        d.init(DatabaseScript.usingSql("create table r1(id text)"));
      })
      .toServicelet();

  public static void init(TesterFacets t) {
    t.servicelets(A, B)
        .http(h -> {
          h.require(MessageOfTheDay.class);
          h.require(HypeOfMoment.class);
        })
        .database(d -> {
          d.using(B).repository(SomeRepo.class).name("repo");
          d.using(B).sql("create table r2(id text)");
        });
  }

  @Inject
  MessageOfTheDay motd;

  @Inject
  HypeOfMoment hype;

  @Inject
  @Named("repo")
  SomeRepo repo;

  @Test
  public void databaseInit() throws SQLException {
    int result = -1;
    try (var handle = repo.connectionHandle();
        var statement = handle.connection.createStatement()) {
      var resultSet = statement.executeQuery("select count(id) from r1 union all select count(id) from r2");
      resultSet.next();
      result = resultSet.getInt(1);
    }
    that(result).is(0);
  }

  @Test
  public void proxyOneHop() {
    that(motd.get()).is("FTW");
  }

  @Test
  public void proxyTwoHops() {
    that(hype.get()).is("!FTW!");
  }

  private static boolean classInitialized;

  @BeforeClass
  public static void beforeClass() {
    classInitialized = true;
  }

  @Test
  public void beforeClassWorked() {
    that(classInitialized).orFail("runner had not called before class hook");
  }

  private boolean initialized;

  @Before
  public void before() {
    initialized = true;
  }

  @After
  public void after() {
    initialized = false;
  }

  @Test
  public void beforeWorked() {
    that(initialized).orFail("runner had not called before hook");
  }
}
