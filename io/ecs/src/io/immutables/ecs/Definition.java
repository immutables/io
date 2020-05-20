package io.immutables.ecs;

import com.google.common.collect.Maps;
import io.immutables.collect.Vect;
import java.util.List;
import java.util.Map;
import org.immutables.data.Data;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;

@Data
@Enclosing
public interface Definition {

	String name();

	@Immutable
	interface Module {
		String name();

		List<Definition> definitions();

		Map<String, String> sources();

		@Lazy
		default Map<String, Definition> byName() {
			return Maps.uniqueIndex(definitions(), d -> d.name());
		}

		class Builder extends ImmutableDefinition.Module.Builder {}
	}

	@Immutable
	interface SourceRange {
		@Default
		default String file() {
			return "~";
		}
		@Default
		default int position() {
			return 0;
		}
		@Default
		default int length() {
			return 0;
		}

		class Builder extends ImmutableDefinition.SourceRange.Builder {}
	}

	interface Commented {
		@Default
		default String comment() {
			return "";
		}
	}

	interface Parameterizable extends Definition {
		Vect<Type.Var> parameters();
	}

	@Immutable
	interface DataTypeDefinition extends Parameterizable, Commented {

		Constructor constructor();

		class Builder extends ImmutableDefinition.DataTypeDefinition.Builder {}
	}

	@Immutable
	interface CaseTypeDefinition extends Parameterizable, Commented {
		Map<String, Constructor> constructors();

		class Builder extends ImmutableDefinition.CaseTypeDefinition.Builder {}
	}

	@Immutable
	interface Constructor {

		Vect<Feature> features();

		class Builder extends ImmutableDefinition.Constructor.Builder {}
	}

	@Immutable
	interface Feature extends Commented {

		String name();

		Type type();

		class Builder extends ImmutableDefinition.Feature.Builder {}
	}
}
