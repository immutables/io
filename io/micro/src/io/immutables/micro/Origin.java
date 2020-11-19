package io.immutables.micro;

import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import org.immutables.data.Data;
import org.immutables.value.Value;
import static com.google.common.base.Preconditions.checkState;

/**
 * Describes origin, a source from where some configuration or some resource comes from. Use best efforts to create most
 * informative and clear origin chains.
 * <p>
 * There's definitely a bit of speculation in this design as it's not clear what parts of it would be would be
 * quintessentially useful.
 */
@Data
@Value.Immutable(singleton = true)
public abstract class Origin implements WithOrigin {
  /**
   * Usually in form or URI, filename or name reference.
   */
  @Value.Default
  public String resource() {
    return "<unspecified>";
  }

  /**
   * If this value is a fallback (or default) value used in case that original resource is not available. The original
   * resource can specified as {@link #resource()} or if the fallback has it's own origin, it can have failed linked via
   * {@link #descends()} attribute.
   */
  @Value.Default
  public boolean isFallback() {
    return false;
  }

  /**
   * Short information / description of fallback value. Some resources/configs are big enough to not include here, but
   * for smaller fallback values or some description of empty values can be included. This can be included regardless if
   * this fallback value would be actually used.
   */
  @Value.Default
  public String fallbackInfo() {
    return "";
  }

  @Value.Default
  public boolean notAvailable() {
    return false;
  }

  /**
   * Optionally, there can be inner path inside the resource.
   */
  @Value.Default
  public String innerPath() {
    return "";
  }

  public abstract OptionalInt atLine();

  /**
   * Exception token attached as evidence of failure leading to resource not available. If {@code exception()} is set
   * then {@link #notAvailable()} must be {@code true}.
   */
  public abstract Optional<ExceptionToken> exception();

  /**
   * Links origin from which this origin "descends" from either by overriding/superseding acting as a substitute or
   * fallback, or specializing.
   */
  public abstract Optional<Origin> descends();

  public boolean isUnspecified() {
    return this == unspecified();
  }

  public Optional<Origin> ifSpecified() {
    return isUnspecified() ? Optional.empty() : Optional.of(this);
  }

  @Value.Check
  void check() {
    checkState(exception().isEmpty() || notAvailable(),
        "when 'exception' is included, 'notAvailable' must be set to true");
  }

  public static Origin unspecified() {
    return ImmutableOrigin.of();
  }

  public static class Builder extends ImmutableOrigin.Builder {
    public Builder resourceFromStackTrace() {
      String herePrefix = Origin.class.getName();
      for (StackTraceElement e : new Throwable().getStackTrace()) {
        if (!e.getClassName().startsWith(herePrefix)) {
          @Nullable String file = e.getFileName();
          resource(e.getClassName() + "." + e.getMethodName());
          if (file != null) innerPath(file);
          int line = e.getLineNumber();
          if (line > 0) atLine(line);
          break;
        }
      }
      return this;
    }
  }

  /**
   * Interface for [configuration] objects for which origin is traced.
   * @param <S> self type for returning copies
   */
  public interface Traced<S extends Traced<S>> {
    @Data.Ignore
    @Value.Auxiliary
    @Value.Default
    default Origin origin() {
      return Origin.unspecified();
    }

    S withOrigin(Origin origin);
  }
}
