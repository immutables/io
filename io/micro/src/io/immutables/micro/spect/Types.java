package io.immutables.micro.spect;

import io.immutables.collect.Vect;
import org.immutables.data.Data;
import org.immutables.value.Value;

@Data
@Value.Enclosing
public interface Types {

  @Value.Immutable
  abstract class Reference {
    public enum Kind { INTRACTABLE, SCALAR, STRUCT, OPTIONAL, LIST, SET, MAP, CONTRACT, RECORD, TYPEVAR }

    public abstract String module();
    public abstract String name();
    public abstract Vect<Reference> arguments();

    public @Value.Default Kind kind() { return Kind.STRUCT; }

    public String toString() {
      return kind() == Kind.TYPEVAR
          ? name()
          : (module() + ":" + name() + (arguments().isEmpty() ? "" : arguments().join(", ", "<", ">")));
    }

    public static class Builder extends ImmutableTypes.Reference.Builder {}
  }

  @Value.Immutable
  interface Struct {
    Reference reference();
    boolean inline();
    Vect<Feature> features();
    Vect<Struct> cases();

    class Builder extends ImmutableTypes.Struct.Builder {}
  }

  @Value.Immutable
  interface Feature {
    String name();
    Reference type();
    boolean nullable();
    boolean hasDefault();
    boolean writeOnly();
    boolean readOnly();

    class Builder extends ImmutableTypes.Feature.Builder {}
  }
}
