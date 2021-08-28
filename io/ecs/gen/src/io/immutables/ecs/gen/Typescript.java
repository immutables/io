package io.immutables.ecs.gen;

import io.immutables.collect.Vect;
import io.immutables.ecs.def.Type;
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import org.immutables.generator.Generator;

@Generator.Template
abstract class Typescript extends Support {
  final Function<Vect<?>, String> genericf = vars -> vars.isEmpty() ? "" : vars.join(", ", "<", ">");
  final Function<Type.Parameterizable, String> generics = t -> genericf.apply(t.parameters());
  final Function<Type, String> typef = t -> t.accept(typeFormatter, null);

  static final Type.Visitor<Void, String> typeFormatter = new Type.Visitor<>() {
    @Override public String variable(Type.Variable v, Void in) {
      return v.toString();
    }

    @Override public String reference(Type.Reference d, Void in) {
      if (d.module().equals("system")) {
        switch (d.name()) {
        case "String": return "string";
        case "i32":
        case "u32":
        case "Int":
        case "i64":
        case "u64":
        case "Long":
        case "f32":
        case "Float":
        case "f64":
        case "Double": return "number";
        case "Bool": return "boolean";
        }
      }
      // currently we rely on generated imports
      //d.module() + "." + d.name()
      return d.name();
    }

    @Override public String parameterized(Type.Parameterized d, Void in) {
      return d.reference().accept(this, in)
          + "<" + d.arguments().map(a -> a.accept(this, null)).join(", ") + ">";
    }

    @Override public String product(Type.Product p, Void in) {
      return "[]";
    }

    @Override public String empty(Void in) {
      return "[]";
    }

    @Override public String array(Type.Array a, Void in) {
      return a.component().accept(this, null) + "[]";
    }

    @Override public String setn(Type.Setn s, Void in) {
      return s.component().accept(this, null) + "[]";
    }

    @Override public String option(Type.Option o, Void in) {
      return o.component().accept(this, null) + " | undefined";
    }

    @Override public String mapn(Type.Mapn m, Void in) {
      return "{[_: string]: " + m.value().accept(this, null) + "}";
    }

    @Override public String otherwise(Type t, Void in) {
      return t.toString();
    }
  };
}
