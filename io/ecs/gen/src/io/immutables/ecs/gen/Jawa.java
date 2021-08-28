package io.immutables.ecs.gen;

import io.immutables.collect.Vect;
import io.immutables.ecs.def.Definition;
import io.immutables.ecs.def.Model;
import io.immutables.ecs.def.Type;
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import org.immutables.generator.Builtins;
import org.immutables.generator.Generator;
import org.immutables.generator.Intrinsics;
import org.immutables.generator.Templates;
import org.immutables.generator.Templates.Invokable;

@Generator.Template
abstract class Jawa extends Support {
  final Function<Vect<?>, String> genericf = vars -> vars.isEmpty() ? "" : vars.join(", ", "<", ">");
  final Function<Type.Parameterizable, String> generics = t -> genericf.apply(t.parameters());
  final Function<Type.Parameterizable, String> diamond = t -> t.parameters().isEmpty() ? "" : "<>";
  final Function<Type.Parameterizable, String> unknown = t -> genericf.apply(t.parameters().map(__ -> "?"));
  final Predicate<Definition.DataTypeDefinition> isInline = t -> t.hasConcept(Compiler.systemInline)
			|| t.isEnum();
/*
  final Predicate<Type.Constrained> isHttpGet = t -> t.hasConcept(Compiler.httpGet);
  final Predicate<Type.Constrained> isHttpPost = t -> t.hasConcept(Compiler.httpPost);
  final Predicate<Type.Constrained> isHttpPut = t -> t.hasConcept(Compiler.httpPut);
  final Predicate<Type.Constrained> isHttpDelete = t -> t.hasConcept(Compiler.httpDelete);
  final Predicate<Type.Constrained> isHttpHead = t -> t.hasConcept(Compiler.httpHead);
  final Predicate<Type.Constrained> hasPath = t -> t.getConstaintFeature("path").isPresent();
  final Function<Type.Constrained, String> getPath = t -> t.getConstraintFirstArgument("path")
      .map(Object::toString)
      .orElse("!%%%!");
*/
  final Predicate<Type.Constrained> hasHttpMethod = t -> t.getMethodUri().flatMap(m -> m.method()).isPresent();
  final Function<Type.Constrained, String> getHttpMethod = t -> t.getMethodUri().flatMap(m -> m.method())
      .orElse("!METHOD!");
  final Predicate<Type.Constrained> hasHttpUri = t -> t.getMethodUri().flatMap(m -> m.uri()).isPresent();
  final Function<Type.Constrained, String> getHttpUri = t -> t.getMethodUri().flatMap(m -> m.uri())
      .orElse("!URI!");

  final Templates.Binary<Type.Constrained, String, Boolean> isHttpPathParameter =
      (t, p) -> t.getMethodUri().map(m -> m.pathParameters().contains(p)).orElse(false);

  final Templates.Binary<Type.Constrained, String, Boolean> isHttpQueryParameter =
      (t, p) -> t.getMethodUri().map(m -> m.queryParameters().contains(p)).orElse(false);

  final Predicate<Vect<?>> small = v -> v.size() <= 5; //v.size() > 0 &&

  final Function<Type, String> typew = t -> t.accept(typeFormatter, wrapped());
  final Function<Type, String> typef = t -> t.accept(typeFormatter, new TypeOpts());

  final String rs = "javax.ws.rs.";
  final String svc = "io.immutables.ecs.service.";
  final String imv = "org.immutables.value.Value.";
  final String ju = "java.util.";

  final Invokable prependVar = new Invokable() {
    @Override public Invokable invoke(Templates.Invokation invokation, Object... parameters) {
      String v = (String) parameters[0];
      Type.Parameterizable t = (Type.Parameterizable) parameters[1];
      String result = t.parameters().map(Object::toString).prepend(v).join(", ", "<", ">");
      invokation.out(result);
      return null;
    }
  };

  static TypeOpts wrapped() {
    TypeOpts opts = new TypeOpts();
    opts.wrap = true;
    return opts;
  }

  static final class TypeOpts {
    boolean wrap;
  }

  static final Type.Visitor<TypeOpts, String> typeFormatter = new Type.Visitor<>() {
    @Override public String variable(Type.Variable v, TypeOpts in) {
      return v.toString();
    }

    @Override public String reference(Type.Reference d, TypeOpts in) {
      if (d.module().equals("system")) {
        switch (d.name()) {
        case "String": return "String";
        case "i32":
        case "u32":
        case "Int": return in.wrap ? "Integer" : "int";
        case "i64":
        case "u64":
        case "Long": return in.wrap ? "Long" : "long";
        case "f32":
        case "Float": return in.wrap ? "Float" : "float";
        case "f64":
        case "Double": return in.wrap ? "Double" : "double";
        case "Bool": return in.wrap ? "Boolean" : "boolean";
        }
      }
      return d.module() + "." + d.name();
    }

    @Override public String parameterized(Type.Parameterized d, TypeOpts in) {
      return d.reference().accept(this, in)
          + "<" + d.arguments().map(a -> a.accept(this, wrapped())).join(", ") + ">";
    }

    @Override public String product(Type.Product p, TypeOpts in) {
      return "Void";
    }

    @Override public String empty(TypeOpts in) {
      return "Void";
    }

    @Override public String array(Type.Array a, TypeOpts in) {
      return "java.util.List<" + a.component().accept(this, wrapped()) + ">";
    }

    @Override public String setn(Type.Setn s, TypeOpts in) {
      return "java.util.Set<" + s.component().accept(this, wrapped()) + ">";
    }

    @Override public String option(Type.Option o, TypeOpts in) {
      return "java.util.Optional<" + o.component().accept(this, wrapped()) + ">";
    }

		@Override public String mapn(Type.Mapn m, TypeOpts in) {
			return "java.util.Map<" + m.key().accept(this, wrapped()) + ", " + m.value().accept(this, wrapped()) + ">";
		}

		@Override public String otherwise(Type t, TypeOpts in) {
      return t.toString();
    }
  };
}
