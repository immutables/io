package io.immutables.ecs.def;

import io.immutables.collect.Vect;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.immutables.data.Data;
import org.immutables.value.Value.*;
import static com.google.common.base.Preconditions.checkState;

@Data
@Enclosing
public interface Definition {

  String name();

  default void forEachType(Consumer<Type> forType) {}

  @Immutable
  interface Module extends ImmutableDefinition.WithModule {
    String name();

    Vect<Definition> definitions();

    Map<String, String> sources();

    default String toTypeName() {
      return CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, name().replace('.', '-'));
    }

    default @Lazy Map<String, Definition> byName() {
      return Maps.uniqueIndex(definitions(), Definition::name);
    }

    default void forEachType(Consumer<Type> forType) {
      for (var c : definitions()) {
        c.forEachType(forType);
      }
    }

    default @Lazy Map<String, Set<Type.Reference>> typeUsage() {
      var byModule = new HashMap<String, Set<Type.Reference>>();
      var collector = new Type.Traversal<Void>() {
        @Override public Void reference(Type.Reference d, Void in) {
          // obviously self usage or using system doesn't count
          // can remove the check from here and put it into template
          // which generates imports, but it's here for now
          if (!d.module().equals(name())
              && !d.module().equals("system")
              && !d.module().equals("ecs")) {
            byModule.computeIfAbsent(d.module(), k -> new HashSet<>()).add(d);
          }
          return null;
        }
        @Override public Void otherwise(Type t, Void in) {
          return null;
        }
      };
      forEachType(t -> t.accept(collector, null));
//    System.err.println("USAGES from " + name() + ">>\\n" + byModule);
      return Map.copyOf(Maps.transformValues(byModule, Set::copyOf));
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

    @Override
    default void forEachType(Consumer<Type> forType) {
      for (var c : constraints()) {
        c.forEachType(forType);
      }
      for (var c : constructors().values()) {
        c.forEachType(forType);
      }
      for (var c : features()) {
        c.forEachType(forType);
      }
    }

    class Builder extends ImmutableDefinition.DataTypeDefinition.Builder {}
  }

  @Immutable
	interface EntityDefinition extends OfEntity, Commented, ImmutableDefinition.WithEntityDefinition {
		Constructor constructor();
  	class Builder extends ImmutableDefinition.EntityDefinition.Builder {}

    default void forEachType(Consumer<Type> forType) {
      constructor().forEachType(forType);
      for (var f : features()) {
        f.forEachType(forType);
      }
    }
	}

  @Immutable
  interface ConceptDefinition extends OfConcept, Commented {
    @Override
    default void forEachType(Consumer<Type> forType) {
      for (var c : constraints()) {
        c.forEachType(forType);
      }
      for (var c : features()) {
        c.forEachType(forType);
      }
    }

    class Builder extends ImmutableDefinition.ConceptDefinition.Builder {}
  }

  @Immutable
  interface ContractDefinition extends OfContract, Commented {
    @Override
    default void forEachType(Consumer<Type> forType) {
      for (var c : constraints()) {
        c.forEachType(forType);
      }
      for (var c : features()) {
        c.forEachType(forType);
      }
    }

    class Builder extends ImmutableDefinition.ContractDefinition.Builder {}
  }

  @Immutable
  abstract class NamedParameter implements Type.Constrained, Commented, ImmutableDefinition.WithNamedParameter {
    public abstract @Parameter String name();
    public abstract @Parameter Type type();
    public abstract @Parameter boolean hasSyntheticName(); // switch to default (Datatypes Fix)

		public @Default String comment() {
			return "";
		}

    public void forEachType(Consumer<Type> forType) {
      constraints().forEach(c -> c.forEachType(forType));
      forType.accept(type());
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
    default void forEachType(Consumer<Type> forType) {
      inlines().forEach(forType);
      Vect<NamedParameter> ps = !mergedParameters().isEmpty() ? mergedParameters() : parameters();
      ps.forEach(p -> p.forEachType(forType));
    }
    class Builder extends ImmutableDefinition.Constructor.Builder {}
  }
}
