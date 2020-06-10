package io.immutables.ecs;

import com.google.common.collect.Maps;
import io.immutables.collect.Vect;
import java.util.Map;
import org.immutables.data.Data;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import org.immutables.value.Value.Parameter;

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

	interface TypeSignature extends Type.Parameterizable, Type.Named {
		Vect<Constraint> constraints();
	}

	interface OfType extends Definition, TypeSignature, FeatureSet {}

	interface OfConcept extends Definition, TypeSignature, FeatureSet {}

	@Immutable
	interface DataTypeDefinition extends OfType, Commented, ImmutableDefinition.WithDataTypeDefinition {
		Constructor constructor();

		class Builder extends ImmutableDefinition.DataTypeDefinition.Builder {}
	}

	@Immutable
	interface CaseTypeDefinition extends OfType, Commented, ImmutableDefinition.WithCaseTypeDefinition {
		Map<String, Constructor> constructors();

		class Builder extends ImmutableDefinition.CaseTypeDefinition.Builder {}
	}

	@Immutable
	interface ConceptDefinition extends OfConcept, Commented {
		class Builder extends ImmutableDefinition.ConceptDefinition.Builder {}
	}

	@Immutable
	abstract class NamedParameter implements ImmutableDefinition.WithNamedParameter {
		public abstract @Parameter String name();
		public abstract @Parameter Type type();
		public abstract @Parameter boolean hasSyntheticName(); // switch to default (Datatypes Fix)
		// TODO default value?

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
		default boolean takesProduct() {
			return !takesRecord();
		}
		default Type toType() {
			if (takesRecord()) {
				// mergedParameters() ?
				return Type.Record.of(parameters().map(p -> Type.Feature.field(p.name(), p.type())));
			}
			if (parameters().size() > 1) {
				return Type.Product.of(parameters().map(p -> p.type()));
			}
			if (parameters().size() == 1) {
				return parameters().first().type();
			}
			return Type.Empty.of();
		}

		class Builder extends ImmutableDefinition.Constructor.Builder {}
	}
}
