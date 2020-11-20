package io.immutables.ecs.def;

import io.immutables.collect.Vect;
import java.util.Optional;
import org.immutables.data.Data;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Data
@Enclosing
@Immutable
public interface Model {
  Vect<DataType> dataTypes();

  Vect<Component> components();

  Vect<ContractType> contracts();

  class Builder extends ImmutableModel.Builder {}

  interface InModule {
    Definition.Module module();
  }

  @Immutable
  interface DataType extends InModule {
    @Parameter @Override Definition.Module module();
    @Parameter Definition.DataTypeDefinition definition();

    static DataType of(Definition.Module m, Definition.DataTypeDefinition t) {
      return ImmutableModel.DataType.of(m, t);
    }
  }

  @Immutable
  interface ContractType extends InModule {
    @Parameter @Override Definition.Module module();
    @Parameter Definition.ContractDefinition definition();

    static ContractType of(Definition.Module m, Definition.ContractDefinition t) {
      return ImmutableModel.ContractType.of(m, t);
    }
  }

  @Immutable
  interface Component extends InModule {
    Definition.DataTypeDefinition definition();
    Optional<Definition.NamedParameter> slug();
    Definition.NamedParameter entity();
    Definition.NamedParameter component();

    // Guava optional used for compatibility with template engine
    default com.google.common.base.Optional<Definition.NamedParameter> slugOpt() {
      return com.google.common.base.Optional.fromJavaUtil(slug());
    }

    class Builder extends ImmutableModel.Component.Builder {}
  }
}
