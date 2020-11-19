package io.immutables.micro;

import java.net.URI;
import java.util.Set;
import com.google.common.net.HostAndPort;
import org.immutables.data.Data;
import org.immutables.value.Value;

// TODO Rename RuntimeInfo or something else?
@Data
@Value.Immutable
@Value.Enclosing
public interface MicroInfo extends Origin.Traced<MicroInfo> {
  long pid();
  String at();
  HostAndPort listen();
  Set<Servicelet.Name> servicelets();
  Set<DatabaseInfo> databases();

  @Value.Immutable
  interface DatabaseInfo {
    Servicelet.Name servicelet();
    URI connection();
    String database();
    Databases.Setup.Isolate isolate();

    class Builder extends ImmutableMicroInfo.DatabaseInfo.Builder {}
  }

  default String key() {
    return keyOf(pid(), at());
  }

  static String keyOf(long pid, String at) {
    return pid + "@" + at;
  }

  class Builder extends ImmutableMicroInfo.Builder {}
}
