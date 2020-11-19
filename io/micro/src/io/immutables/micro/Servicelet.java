package io.immutables.micro;

import com.google.inject.Module;
import org.immutables.data.Data;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Data
@Value.Enclosing
public interface Servicelet {

  Manifest manifest();

  Module module();

  /**
   * Symbolic name for servicelet, should be suitable to be used as a slug (i.e. URI segment). Relatively unique, may be
   * not absolutely unique, but then it would be a problem to mix together under single deployment. By convention it
   * should be in kebab case, lowercase with dashes (hyphen/minus sign) as separators.
   */
  @Data.Inline
  @Immutable(builder = false)
  abstract class Name {
    @Parameter
    abstract String value();

    @Override
    public String toString() {
      return value();
    }
  }

  static Name name(String value) {
    return ImmutableServicelet.Name.of(value);
  }

  default Name name() {
    return manifest().name();
  }
}
