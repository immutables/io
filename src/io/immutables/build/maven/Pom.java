package io.immutables.build.maven;

import com.google.gson.annotations.SerializedName;
import io.immutables.grammar.Escapes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.immutables.gson.Gson.TypeAdapters;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@TypeAdapters
@Enclosing
final class Pom {
	private Pom() {}

	interface Definitions {
		@Default
		default Dependencies dependencies() {
			return ImmutablePom.Dependencies.of();
		}

		@Default
		default DependencyManagement dependencyManagement() {
			return ImmutablePom.DependencyManagement.of();
		}

		@Default
		default Profiles profiles() {
			return ImmutablePom.Profiles.of();
		}

		@Default
		default ModulePaths modules() {
			return ImmutablePom.ModulePaths.of();
		}
	}

	@Immutable(singleton = true)
	interface Dependencies {
		Set<DeclaredDependency> dependency();

		class Builder extends ImmutablePom.Dependencies.Builder {}
	}

	@Immutable(singleton = true)
	interface DependencyManagement {
		Set<DeclaredDependency> dependency();

		class Builder extends ImmutablePom.DependencyManagement.Builder {}
	}

	@Immutable(singleton = true)
	interface ModulePaths {
		Set<Str> module();

		class Builder extends ImmutablePom.ModulePaths.Builder {}
	}

	@Immutable(singleton = true)
	interface Profiles {
		List<Profile> profile();

		class Builder extends ImmutablePom.Profiles.Builder {}
	}

	@Immutable
	interface Profile extends Definitions {
		String id();

		class Builder extends ImmutablePom.Profile.Builder {}
	}

	@Immutable
	interface Project extends DeclaresGroupArtifactVersion, Definitions {
		Optional<Parent> parent();
		Optional<Str> name();
		Optional<Str> description();

		class Builder extends ImmutablePom.Project.Builder {}
	}

	@Immutable
	interface DeclaredDependency extends DeclaresGroupArtifactVersion {

		Optional<String> classifier();

		@Default
		default Scope scope() {
			return Scope.COMPILE;
		}

		@Default
		default boolean optional() {
			return false;
		}

		class Builder extends ImmutablePom.DeclaredDependency.Builder {}
	}

	interface ResolvedCoordinates {
		GroupId groupId();
		ArtifactId artifactId();
		Version version();
	}

	@Immutable
	interface Module extends ResolvedCoordinates {

		class Builder extends ImmutablePom.Module.Builder {}
	}

	@Immutable
	interface Artifact extends ResolvedCoordinates {

		class Builder extends ImmutablePom.Artifact.Builder {}
	}

	enum Scope {
		@SerializedName("compile")
		COMPILE,
		@SerializedName("provided")
		PROVIDED,
		@SerializedName("runtime")
		RUNTIME,
		@SerializedName("test")
		TEST,
		@SerializedName("import")
		IMPORT,
		@SerializedName("system")
		SYSTEM
	}

	enum Kind {
		@SerializedName("jar")
		JAR,
		@SerializedName("war")
		WAR,
		@SerializedName("pom")
		POM,
		@SerializedName("sources-jar")
		SOURCES_JAR,
		@SerializedName("test-jar")
		TEST_JAR
	}

	@Immutable
	interface Properties {
		@Parameter
		Map<String, Str> entries();
	}

	interface DeclaresGroupArtifactVersion {
		Optional<Str> groupId();
		Optional<Str> artifactId();
		Optional<Str> version();
	}

	@Immutable
	interface Parent extends DeclaresGroupArtifactVersion {

		class Builder extends ImmutablePom.Parent.Builder {}
	}

	@Immutable
	abstract static class GroupId extends Wrap<String> {}

	@Immutable
	abstract static class ArtifactId extends Wrap<String> {}

	@Immutable
	abstract static class Version extends Wrap<String> {}

	/** String value wrapper that can be also evaluated (having interpolated expression). */
	@Immutable
	abstract static class Str extends Wrap<String> {
		static final Str Empty = ImmutablePom.Str.of("");

		@Override
		public String toString() {
			return Escapes.singleQuote(value());
		}
	}

	private abstract static class Wrap<T> {
		@Parameter
		public abstract T value();

		@Override
		public String toString() {
			return getClass().getSimpleName() + "(" + value() + ")";
		}
	}
}
