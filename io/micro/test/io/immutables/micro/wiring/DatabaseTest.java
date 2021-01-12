package io.immutables.micro.wiring;


import io.immutables.micro.*;

import io.immutables.micro.wiring.docker.DockerRunner;
import io.immutables.regres.SqlAccessor;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import org.junit.BeforeClass;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class DatabaseTest {
  interface Repo extends SqlAccessor {}

  private static final Servicelet.Name s1 = Servicelet.name("s1");

  private static final Injector injector = new Launcher()
      .add(new ServiceletNameModule())
      .add(new ServiceManagerModule())
      .add(new DatabaseModule())
      .add(new JsonModule())
      .add(binder -> {
        binder.bind(Databases.Setup.class).toInstance(
            new Databases.Setup.Builder()
                .autostart(true)
                .database("<random>")
                .isolate(Databases.Setup.Isolate.SCHEMA)
                .mode(Databases.Setup.Mode.CREATE_AND_DROP)
                .connect(String.format(
                    "postgresql://localhost:%d/postgres", 5432/* PORT */))
                //"postgresql://localhost:%d/defaultdb?user=root&password=&sslmode=disable", PORT))
                .build());
      })
      .addServicelet(new Module() {
        @Override
        public void configure(Binder binder) {
          ServiceletNameModule.assignName(binder, s1);

          binder.bind(Manifest.class).toInstance(deriveManifestRequireDatabase());

          Key<Repo> repo = Key.get(Repo.class);
          binder.bind(repo).toProvider(Databases.repositoryOwnProvider(repo));
          Multibinder.newSetBinder(binder, DatabaseScript.class)
              .addBinding()
              .toInstance(DatabaseScript.usingSql("create table roachie(id text)"));
        }

        private Manifest deriveManifestRequireDatabase() {
          return new Manifest.Builder()
              .name(s1)
              .addResources(new Manifest.Resource.Builder()
                  .reference(References.reference(Void.class))
                  .kind(Manifest.Kind.DATABASE_RECORD)
                  .build())
              .build();
        }
      })
      .inject();

  //@BeforeClass
  public static void ensureListening() {
    DockerRunner.assertPostgresRunning(26259);
  }

  @Test
  public void prepareAndUseTestDatabase() throws SQLException {
    ServiceManager manager = injector.getInstance(ServiceManager.class);
    manager.startAsync().awaitHealthy();

    Databases.RepositoryFactory factory = injector.getInstance(Databases.RepositoryFactory.class);
    Repo repo = (Repo) factory.create(s1, Key.get(Repo.class));

    int result = -1;
    try (var h = repo.connectionHandle(); var s = h.connection.createStatement()) {
      ResultSet resultSet = s.executeQuery("select count(*) from roachie");
      resultSet.next();
      result = resultSet.getInt(1);
    }

    that(result).is(0); // just 0, but we didn't failed on non existent SQL objects
    manager.stopAsync().awaitStopped();
  }
}
