package io.immutables.micro.spect;

import io.immutables.collect.Vect;
import io.immutables.micro.Manifest;
import io.immutables.micro.Origin;
import java.util.Map;
import java.util.Optional;
import org.immutables.data.Data;
import org.immutables.value.Value;
import static com.google.common.base.Preconditions.checkState;

@Data
@Value.Immutable
@Value.Enclosing
public interface ManifestCatalog extends Origin.Traced<ManifestCatalog> {
  Vect<Manifest> manifests();
  Vect<Contract> contracts();
  Vect<Types.Struct> structs();
  Vect<Record> records();
  Map<Manifest.Reference, Types.Reference> resourceTypes();

  class Builder extends ImmutableManifestCatalog.Builder {}

  @Value.Immutable
  interface Record {
    Types.Reference reference();
    Types.Reference contract();

    Feature entity();
    Optional<Feature> slug();
    Feature component();

    @Value.Check default void check() {
      checkState(reference().kind() == Types.Reference.Kind.RECORD);
    }

    class Builder extends ImmutableManifestCatalog.Record.Builder {}

    @Value.Immutable
    interface Feature {
      @Value.Parameter String name();
      @Value.Parameter Types.Reference type();

      static Feature of(String name, Types.Reference type) {
        return ImmutableManifestCatalog.Feature.of(name, type);
      }
    }
  }

  @Value.Immutable
  interface Contract {
    Types.Reference reference();
    Vect<Operation> operations();

    @Value.Check default void check() {
      checkState(reference().kind() == Types.Reference.Kind.CONTRACT);
    }

    class Builder extends ImmutableManifestCatalog.Contract.Builder {}
  }

  @Value.Immutable
  interface Operation {
    enum Method { GET, PUT, POST }
    String name();
    String path();
    Optional<Method> method();
    Types.Reference returns();
    Vect<Parameter> parameters();

    class Builder extends ImmutableManifestCatalog.Operation.Builder {}
  }

  @Value.Immutable
  interface Parameter {
    enum Kind { PATH, QUERY, HEADER, MATRIX, ENTITY } // TODO Form, Cookie
    Kind kind();
    String name();
    Types.Reference type();
    Optional<String> defaults();

    class Builder extends ImmutableManifestCatalog.Parameter.Builder {}
  }

  static ManifestCatalog collectFrom(Iterable<Manifest> manifests) {
    return new CatalogCollector().collectFrom(manifests);
  }
}
