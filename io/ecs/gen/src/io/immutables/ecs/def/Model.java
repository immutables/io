package io.immutables.ecs.def;

import io.immutables.Nullable;
import io.immutables.collect.Vect;
import java.util.Optional;
import org.immutables.data.Data;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Data
@Enclosing
@Immutable
public interface Model {
  Vect<DataType> dataTypes();
  Vect<Contract> contracts();
	Vect<Entity> entities();
	Vect<Component> components();
	Vect<Definition.Module> modules();

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
  interface Contract extends InModule {
    @Parameter @Override Definition.Module module();
    @Parameter Definition.ContractDefinition definition();

    static Contract of(Definition.Module m, Definition.ContractDefinition t) {
      return ImmutableModel.Contract.of(m, t);
    }
  }

	@Immutable
	interface Entity extends InModule {
		@Parameter @Override Definition.Module module();
		@Parameter Definition.EntityDefinition definition();

		static Entity of(Definition.Module m, Definition.EntityDefinition t) {
			return ImmutableModel.Entity.of(m, t);
		}
	}

  @Immutable
  interface Component extends InModule {
    String name();
    Optional<Definition.NamedParameter> slug();
    Definition.NamedParameter entity();
    Definition.NamedParameter component();
    default @Default String comment() {
    	return "";
		}

    // Guava optional used for compatibility with template engine
    default com.google.common.base.Optional<Definition.NamedParameter> slugOpt() {
      return com.google.common.base.Optional.fromJavaUtil(slug());
    }

    class Builder extends ImmutableModel.Component.Builder {}
  }

	default @Nullable DataType getDatatype(String module, String name) {
		for (var d : dataTypes()) {
			if (d.module().name().equals(module) && d.definition().name().equals(name)) {
				return d;
			}
		}
		return null;
	}
}
