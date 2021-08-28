package io.immutables.ecs.gen;

import io.immutables.Nullable;
import io.immutables.collect.Vect;
import io.immutables.ecs.def.Definition;
import io.immutables.ecs.def.Model;
import io.immutables.ecs.def.Type;
import com.google.common.base.*;
import org.immutables.generator.Builtins;
import org.immutables.generator.Generator;
import org.immutables.generator.Templates;

abstract class Support extends Builtins {
  Output output;
  Model model;

  abstract Templates.Invokable generate();

  @Generator.Typedef io.immutables.ecs.def.Definition Definition;
  @Generator.Typedef io.immutables.ecs.def.Definition.Module Module;
  @Generator.Typedef io.immutables.ecs.def.Definition.NamedParameter Parameter;
  @Generator.Typedef io.immutables.ecs.def.Definition.Constructor Constructor;
  @Generator.Typedef Definition.DataTypeDefinition Datatype;
  @Generator.Typedef Definition.Module ModuleDefinition;
  @Generator.Typedef Definition.EntityDefinition EntityDefinition;
  @Generator.Typedef io.immutables.ecs.def.Definition.ContractDefinition Contract;
  @Generator.Typedef Model.Component Component;
  @Generator.Typedef Model.Entity EntityModel;
  @Generator.Typedef Model.DataType DataTypeModel;
  @Generator.Typedef Model.Contract ContractModel;
  @Generator.Typedef Type.Feature Feature;
  @Generator.Typedef TypeDecider Typed;

  final Predicate<Definition.TypeSignature> isInline = t -> t.hasConcept(Compiler.systemInline);
  final Predicate<Type> isRequired = t -> new TypeDecider(t).isRequired();

  final Function<Object, String> toHyphen =
      Functions.compose(
          CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_HYPHEN),
          Functions.toStringFunction());

  final Function<Object, String> toUnder =
      Functions.compose(
          CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE),
          Functions.toStringFunction());

  final Function<Type, TypeDecider> decideType = TypeDecider::new;
  final Function<String, Iterable<String>> linesOf = commentBlock -> {
    var lines = Vect.from(Splitter.on('\n').split(commentBlock));
    if (lines.all(l -> l.isEmpty() || l.startsWith(" "))) {
      lines = lines.map(l -> l.isEmpty()  ? l : l.substring(" ".length()));
    }
    if (lines.size() > 1 && lines.last().isBlank()) {
      lines = lines.range(0, lines.size() - 1);
    }
    return lines;
  };

  final Function<Type, Definition.DataTypeDefinition> findDataType = t -> {
    var decider = new TypeDecider(t);
    var dt = decider.getElement().type.ifReference(r -> model.getDatatype(r.module(), r.name()), null);
    return dt != null ? dt.definition() : null;
  };

  final class TypeDecider {
    final Type type;
    private boolean isDataType = true;
    private @Nullable Definition.DataTypeDefinition dataType;

    @Nullable Definition.DataTypeDefinition getDataType() {
      if (!isDataType) return null;
      if (!isReference()) {
        isDataType = false;
        return null;
      }
      if (dataType == null) {
        dataType = findDataType.apply(type);
      }
      if (dataType != null) {
        return dataType;
      } else {
        isDataType = false;
        return null;
      }
    }

    TypeDecider(Type type) {
      this.type = type;
    }

    boolean isScalar() {
      return isSystemScalar() || isEnum() || isInline();
    }

    boolean isEnum() {
      var dt = getDataType();
      return dt != null && dt.isEnum();
    }

    boolean isInline() {
      var dt = getDataType();
      return dt != null && isInline.apply(dt);
    }

    boolean hasCases() {
      var dt = getDataType();
      return dt != null && dt.hasCases() && !dt.isEnum();
    }

    boolean isStruct() {
      var dt = getDataType();
      return dt != null && !isInline.apply(dt) && !dt.hasCases() && !dt.isEnum();
    }

    boolean isArray() {
      return type instanceof Type.Array;
    }

    boolean isSetn() {
      return type instanceof Type.Setn;
    }

    boolean isMapn() {
      return type instanceof Type.Mapn;
    }

    boolean isOption() {
      return type instanceof Type.Option;
    }

    boolean isArrayLike() {
      return isArray() || isSetn();
    }

    boolean isRequired() {
      return !isOption() && !isArrayLike() && !isMapn();
    }

    boolean isReferenceLike() {
      return isReference() || isParameterized();
    }

    boolean isReference() {
      return type instanceof Type.Reference;
    }

    boolean isParameterized() {
      return type instanceof Type.Parameterized;
    }

    boolean isSystem() {
      return type instanceof Type.Reference
          && ((Type.Reference) type).module().equals("system");
    }

    boolean isSystemScalar() {
      switch (isSystem() ? ((Type.Reference) type).name() : "") {
      case "String":
      case "i32":
      case "u32":
      case "Int":
      case "i64":
      case "u64":
      case "Long":
      case "f32":
      case "Float":
      case "f64":
      case "Double":
      case "Bool":
        return true;
      default:
        return false;
      }
    }

    TypeDecider getUnparameterized() {
      if (type instanceof Type.Parameterized) {
        return new TypeDecider(((Type.Parameterized) type).reference());
      }
      return this;
    }

    String getName() {
      if (type instanceof Type.Reference) {
        return ((Type.Reference) type).name();
      }
      if (type instanceof Type.Parameterized) {
        return ((Type.Parameterized) type).reference().name();
      }
      return type.toString();
    }

    String getModule() {
      if (type instanceof Type.Reference) {
        return ((Type.Reference) type).module();
      }
      if (type instanceof Type.Parameterized) {
        return ((Type.Parameterized) type).reference().module();
      }
      return "";
    }

    TypeDecider getElement() {
      return new TypeDecider(type.accept(new Type.Visitor<Void, Type>() {
        @Override
        public Type setn(Type.Setn s, Void in) {
          return s.component();
        }
        @Override
        public Type array(Type.Array a, Void in) {
          return a.component();
        }
        @Override
        public Type option(Type.Option o, Void in) {
          return o.component();
        }

        @Override
        public Type reference(Type.Reference d, Void in) {
          return d;
        }

        @Override
        public Type parameterized(Type.Parameterized d, Void in) {
          return d.reference();
        }

        @Override
        public Type otherwise(Type t, Void in) {
          return t;
        }
      }, null));
    }

    TypeDecider getKey() {
      return new TypeDecider(type.accept(new Type.Visitor<Void, Type>() {
        @Override
        public Type mapn(Type.Mapn m, Void in) {
          return m.key();
        }
        @Override
        public Type otherwise(Type t, Void in) {
          return Type.Empty.of();
        }
      }, null));
    }

    TypeDecider getValue() {
      return new TypeDecider(type.accept(new Type.Visitor<Void, Type>() {
        @Override
        public Type mapn(Type.Mapn m, Void in) {
          return m.value();
        }
        @Override
        public Type otherwise(Type t, Void in) {
          return Type.Empty.of();
        }
      }, null));
    }

    Vect<TypeDecider> getArguments() {
      return type.accept(new Type.Visitor<Void, Vect<Type>>() {
        @Override
        public Vect<Type> setn(Type.Setn s, Void in) {
          return Vect.of(s.component());
        }
        @Override
        public Vect<Type> array(Type.Array a, Void in) {
          return Vect.of(a.component());
        }
        @Override
        public Vect<Type> option(Type.Option o, Void in) {
          return Vect.of(o.component());
        }
        @Override
        public Vect<Type> mapn(Type.Mapn m, Void in) {
          return Vect.of(m.key(), m.value());
        }
        @Override
        public Vect<Type> parameterized(Type.Parameterized d, Void in) {
          return d.arguments();
        }
        @Override
        public Vect<Type> otherwise(Type t, Void in) {
          return Vect.of();
        }
      }, null).map(TypeDecider::new);
    }
  }
}
