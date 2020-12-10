package io.immutables.ecs.def;

import io.immutables.collect.Vect;
import java.util.Map;
import com.google.common.collect.Maps;
import org.immutables.data.Data;
import org.immutables.value.Value.*;
import static com.google.common.base.Preconditions.checkState;

@Data
@Enclosing
public interface Definition {

  String name();

  @Immutable
  interface Module extends ImmutableDefinition.WithModule {
    String name();

    Vect<Definition> definitions();

    Map<String, String> sources();

    default @Lazy Map<String, Definition> byName() {
      return Maps.uniqueIndex(definitions(), Definition::name);
    }

    class Builder extends ImmutableDefinition.Module.Builder {}
  }

  @Immutable
  interface SourceRange {
    default @Default String file() {
      return "~";
    }

    default @Default int position() {
      return 0;
    }

    default @Default int length() {
      return 0;
    }

    class Builder extends ImmutableDefinition.SourceRange.Builder {}
  }

  interface Commented {
    default @Default String comment() {
      return "";
    }
  }

  interface FeatureSet {
    Vect<Type.Feature> features();

    default Type.Feature getFeature(String name) {
      for (Type.Feature f : features()) {
        if (f.name().equals(name)) return f;
      }
      return Type.Feature.missing(name);
    }
  }

	interface HasModule {
		String module();
	}

  interface TypeSignature extends Type.Constrained, Type.Parameterizable, Type.Named, HasModule {}

  interface OfType extends Definition, TypeSignature, FeatureSet {}

  interface OfConcept extends Definition, TypeSignature, FeatureSet {}

  interface OfContract extends Definition, TypeSignature, FeatureSet {}

  interface OfEntity extends Definition, HasModule, Type.Constrained, Type.Named, FeatureSet {}

  @Immutable
  interface DataTypeDefinition extends OfType, Commented, ImmutableDefinition.WithDataTypeDefinition {
    Map<String, Constructor> constructors();
    boolean hasCases();

    default boolean isEnum() {
      return hasCases() && constructors().values().stream().allMatch(Constructor::takesUnit);
    }

    default Constructor constructor() {
      checkState(!hasCases());
      return constructors().get(name());
    }

    class Builder extends ImmutableDefinition.DataTypeDefinition.Builder {}
  }

  @Immutable
	interface EntityDefinition extends OfEntity, Commented, ImmutableDefinition.WithEntityDefinition {
		Constructor constructor();
  	class Builder extends ImmutableDefinition.EntityDefinition.Builder {}
	}

  @Immutable
  interface ConceptDefinition extends OfConcept, Commented {
    class Builder extends ImmutableDefinition.ConceptDefinition.Builder {}
  }

  @Immutable
  interface ContractDefinition extends OfContract, Commented {
    class Builder extends ImmutableDefinition.ContractDefinition.Builder {}
  }

  @Immutable
  abstract class NamedParameter implements Type.Constrained, ImmutableDefinition.WithNamedParameter {
    public abstract @Parameter String name();
    public abstract @Parameter Type type();
    public abstract @Parameter boolean hasSyntheticName(); // switch to default (Datatypes Fix)

		public @Default String comment() {
			return "";
		}

    public static NamedParameter of(String name, Type type) {
      return ImmutableDefinition.NamedParameter.of(name, type, false);
    }
  }

  @Immutable
  interface Constructor extends ImmutableDefinition.WithConstructor {
    Vect<Type> inlines();
    Vect<NamedParameter> parameters();
    Vect<NamedParameter> mergedParameters();
    boolean takesRecord();
    default boolean takesUnit() {
      return !takesRecord() && mergedParameters().isEmpty();
    }
    default boolean takesProduct() {
      return !takesRecord();
    }
    default Type toType() {
      Vect<NamedParameter> ps = !mergedParameters().isEmpty() ? mergedParameters() : parameters();
      if (takesRecord()) {
        return Type.Record.of(ps.map(p -> Type.Feature.field(p.name(), p.type())));
      }
      if (parameters().size() > 1) {
        return Type.Product.of(ps.map(NamedParameter::type));
      }
      if (parameters().size() == 1) {
        return parameters().first().type();
      }
      return Type.Empty.of();
    }
    class Builder extends ImmutableDefinition.Constructor.Builder {}
  }
}
