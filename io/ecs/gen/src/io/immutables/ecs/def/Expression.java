package io.immutables.ecs.def;

import io.immutables.collect.Vect;
import io.immutables.grammar.Escapes;
import java.util.Optional;
import org.immutables.data.Data;
import org.immutables.value.Value;
import static com.google.common.base.Preconditions.checkArgument;

@Data
@Value.Enclosing
public interface Expression {
  interface Literal extends Expression {}

  @Value.Immutable
  abstract class Str implements Literal {
    public abstract @Value.Parameter String value();

    public static Str of(String value) {
      return ImmutableExpression.Str.of(value);
    }

    @Override
    public String toString() {
      return Escapes.doubleQuote(value());
    }
  }

  @Value.Immutable
  abstract class Num implements Literal {
    public abstract @Value.Parameter Number value();

    public static Num of(Number value) {
      return ImmutableExpression.Num.of(value);
    }

    @Override
    public String toString() {
      return value().toString();
    }
  }

  interface BoundedLiteral extends Literal {}

  @Value.Immutable
  abstract class Product implements BoundedLiteral {
    public abstract @Value.Parameter Vect<Expression> components();

    public static Product of(Expression... expressions) {
      return of(Vect.of(expressions));
    }

    public static Product of(Iterable<? extends Expression> expressions) {
      var exps = Vect.<Expression>from(expressions);
      checkArgument(exps.size() > 0, "Use Empty.of() when zero arguments");
      return ImmutableExpression.Product.of(exps);
    }

    @Override
    public String toString() { return components().join(", ", "(", ")"); }
  }

  @Value.Immutable(singleton = true, builder = false)
  abstract class Empty implements BoundedLiteral {
    public static Empty of() {
      return ImmutableExpression.Empty.of();
    }

    @Override
    public String toString() { return "()"; }
  }

  @Value.Immutable
  abstract class Apply implements Expression {
    public abstract @Value.Parameter String feature();
    public abstract @Value.Parameter Optional<BoundedLiteral> argument();

    public static Apply of(String feature, BoundedLiteral argument) {
      return ImmutableExpression.Apply.of(feature, Optional.of(argument));
    }

    public static Apply of(String feature) {
      return ImmutableExpression.Apply.of(feature, Optional.empty());
    }

    @Override
    public String toString() {
      return feature() + argument().map(Object::toString).orElse("");
    }
  }
}
