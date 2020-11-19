package io.immutables.micro;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.immutables.data.Data;
import org.immutables.value.Value;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

/**
 * Servicelet manifest contains only contains "published" (aka public & shared) information about servicelet for
 * integration and service interoperability. The JSON representation of manifest is to be standardized across
 * [micro]platform implementation.
 * <p>
 * By current design, this manifest must exclude any timestamps and version of servicelet (version of manifest format
 * itself can be added, but this is too speculative to add it now) Timestamps and versioning will be applied on top of
 * entries in the service registry including the information from a build/packaging. The reasoning follows that a
 * servicelet itself does not constitute contract in the way that provided resources are. For example, one servicelet
 * can be dependent on the some version of some API and then, in turn, the system will be changed so that this API is
 * still provided, but moved to yet another service. Therefore there's no contractual needs for servicelet level
 * versioning, but there's a regular need for proper snapshot/build/implementation versioning of the microservice (which
 * happens to export the manifest). Such microservice (existing as an container image, for instance) version will be
 * fully depend on the compiled content of the service, enabling content digesting and content addressing (including
 * fully reliable caching and deployment reproduceability). After container image is built, its metadata with
 * timestamps, content version/digest and the exported servicelet manifest would be added to the service registry.
 */
@Data
@Enclosing
@Immutable
public interface Manifest {
  /**
   * Id is URL-segment-friendly symbolic name. This is not expected to be strictly unique across the system (service
   * registry, yet there may be conflicts preventing deploying 2 servicelets having the same id into a group of deployed
   * services if, for example, the id will be used for routing as URI path prefix. Obviously, advanced enough
   * implementation can work around this, but still it's a good idea keep these relatively unique but still short and
   * catchy.
   */
  Servicelet.Name name();

  // future speculation: some tags, categories, group(s)
  // for now let's stay away from something not though out well yet

  Set<Resource> resources();

  @Immutable
  interface Resource {
    Reference reference();

    Kind kind();

    /**
     * declaration line number from the source if we can get it. (not a fancy source range, but just to start with)
     */
    OptionalInt atLine();

    class Builder extends ImmutableManifest.Resource.Builder {}
  }

  @Data.Inline
  @Immutable(builder = false)
  abstract class Reference {
    @Parameter
    public abstract String value();

    @Override
    public String toString() {
      return value();
    }

    public static Manifest.Reference of(String value) {
      return ImmutableManifest.Reference.of(value);
    }
  }

  /**
   * Kinds of resources. Not extendable at this moment, predetermined here in in the structure of servicelet declaration
   * DSL.
   * <p>
   * It seems that there are no absolute symmetry between import/export or produce/consumes, topics or databases can be
   * just required but no servicelet actually export, so instead of doing orthogonal technology / direction(in,out), we
   * start with specific combinations.
   */
  enum Kind {
    HTTP_REQUIRE,
    HTTP_PROVIDE,
    STREAM_PRODUCE,
    STREAM_CONSUME,
    DATABASE_REQUIRE,
    DATABASE_RECORD,
  }

  /**
   * Information about the source of servicelet definition, which is usually imagined as a source file in either
   * structured data format (like JSON, YAML) or, more likely, some DSL (embedded to the host language or externally
   * defined). This information is used for quality of presentation and troubleshooting/tracing, but if now available
   * would have empty file name/source lines.
   */
  @Value.Default
  default SourceInfo source() {
    return new SourceInfo.Builder().file("<unknown>").build();
  }

  // TODO Maybe integrate with Origin
  @Immutable
  interface SourceInfo {
    /**
     * Filename in which servicelet is defined, can include path, either related to the repo root or the module root,
     * but can also be just a file name with no path prefix. Assumption is that not all platform's build /packaging
     * systems would provide such information. (for example, in-memory testing platform can have no path or file name
     * information). Would be empty string if no file name is available.
     */
    String file();

    /**
     * Listing of the source lines. (Here for convenience we choose to use array of lines in JSON, the alternative could
     * be just a single string with line breaks '\n'). Would be empty array if not available.
     */
    List<String> lines();

    // future speculation: maybe some markers like (line, column, message) tuples

    class Builder extends ImmutableManifest.SourceInfo.Builder {}
  }

  class Builder extends ImmutableManifest.Builder {}
}
