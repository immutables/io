package io.immutables.ecs.def;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.immutables.data.Data;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import static com.google.common.base.Preconditions.checkState;

@Data
@Enclosing
public interface Constraint {

  default void forEachType(Consumer<Type> forType) {}

  @Immutable
  abstract class Concept implements Constraint {
    public abstract @Parameter Type type();

    // @Check
    void check() {
      var r = type();
      checkState(r instanceof Type.Reference || r instanceof Type.Parameterized);
    }

    @Override
    public void forEachType(Consumer<Type> forType) {
      forType.accept(type());
    }

    public static Concept of(Type t) {
      return ImmutableConstraint.Concept.of(t);
    }
  }

  @Immutable
  abstract class FeatureApply implements Constraint {
    public abstract @Parameter Expression.Apply expression();

    public static FeatureApply of(Expression.Apply expression) {
      return ImmutableConstraint.FeatureApply.of(expression);
    }

    @Override public String toString() {
      return expression().toString();
    }
  }

	@Immutable
  abstract class MethodUri implements Constraint {
    public abstract Optional<String> method();
    public abstract Optional<String> uri();
    public abstract Set<String> pathParameters();
    public abstract Set<String> queryParameters();

    public static final class Builder extends ImmutableConstraint.MethodUri.Builder {}
  }
}
