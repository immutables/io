package io.immutables.endec;

import java.lang.annotation.Target;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.annotation.meta.TypeQualifier;
import static java.lang.annotation.ElementType.TYPE_USE;

@Target(TYPE_USE)
@TypeQualifier(applicableTo = int.class)
@interface Offset {}

abstract class Out {}

abstract class In {
  public abstract @Offset int offset();
  public abstract void reset(@Offset int offset);
}

interface Type {}

interface Factory {
  @Nullable
  <T> Endec<T> get(Type type);
}

class Registry {
  void add(Factory factory) {}
  void add(Function<Registry, Factory> factory) {}
  Factory toFactory() {
    return null;
  }
}

interface Endec<T> {
  T decode(In in);

  void encode(Out out, T instance);
}
