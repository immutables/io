package io.immutables.micro.spect;

import io.immutables.codec.Datatypes;
import io.immutables.collect.Vect;
import io.immutables.ecs.Ecs;
import io.immutables.micro.Manifest;
import io.immutables.micro.References;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import javax.annotation.Nullable;
import javax.ws.rs.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.immutables.data.Datatype;
import static java.util.Objects.requireNonNull;

final class CatalogCollector {
  private final ManifestCatalog.Builder builder = new ManifestCatalog.Builder();
  private final Map<Class<?>, ManifestCatalog.Contract> contracts = new LinkedHashMap<>();
  private final Map<Class<?>, ManifestCatalog.Record> records = new LinkedHashMap<>();
  private final Map<Class<?>, Types.Struct> structs = new LinkedHashMap<>();
  private final Map<Manifest.Reference, Types.Reference> resourceTypes = new LinkedHashMap<>();

  ManifestCatalog collectFrom(Iterable<Manifest> manifests) {
    for (var m : manifests) {
      for (var r : m.resources()) {
        switch (r.kind()) {
        case HTTP_PROVIDE:
        case HTTP_REQUIRE:
          collectEndpoint(r.reference());
          break;
        case STREAM_CONSUME:
        case STREAM_PRODUCE:
          resourceTypes.computeIfAbsent(r.reference(), this::extractType);
          break;
        case DATABASE_RECORD:
          collectRecord(r.reference());
          break;
        case DATABASE_REQUIRE:
          break;
        }
      }
    }

    builder.addAllManifests(manifests)
        .putAllResourceTypes(resourceTypes)
        .addAllContracts(contracts.values())
        .addAllStructs(structs.values())
        .addAllRecords(records.values());

    return builder.build();
  }

  void collectRecord(Manifest.Reference reference) {
    if (!resourceTypes.containsKey(reference)) {
      Class<?> raw = References.key(reference).getTypeLiteral().getRawType();
      var record = records.computeIfAbsent(raw, k -> extractRecord(raw));
      resourceTypes.put(reference, record.reference());
    }
  }

  ManifestCatalog.Record extractRecord(Class<?> t) {
    var record = new ManifestCatalog.Record.Builder().reference(recordType(t));
    Ecs.Contract contract = t.getAnnotation(Ecs.Contract.class);
    requireNonNull(contract, "@Ecs.Contract");

    record.contract(contractType(contract.value()));

    for (var m : t.getMethods()) {
      if (m.isAnnotationPresent(Ecs.Entity.class)) {
        record.entity(ManifestCatalog.Record.Feature.of(m.getName(),
            extractType(m.getReturnType(), m.getGenericReturnType())));
      }
      if (m.isAnnotationPresent(Ecs.Slug.class)) {
        record.slug(ManifestCatalog.Record.Feature.of(m.getName(),
            extractType(m.getReturnType(), m.getGenericReturnType())));
      }
      if (m.isAnnotationPresent(Ecs.Component.class)) {
        record.component(ManifestCatalog.Record.Feature.of(m.getName(),
            extractType(m.getReturnType(), m.getGenericReturnType())));
      }
    }
    return record.build();
  }

  void collectEndpoint(Manifest.Reference reference) {
    if (!resourceTypes.containsKey(reference)) {
      Class<?> raw = References.key(reference).getTypeLiteral().getRawType();
      var contract = contracts.computeIfAbsent(raw, k -> extractContract(raw));
      resourceTypes.put(reference, contract.reference());
    }
  }

  ManifestCatalog.Contract extractContract(Class<?> t) {
    class Collecter {
      final ManifestCatalog.Contract.Builder contract =
          new ManifestCatalog.Contract.Builder().reference(contractType(t));
      final String commonPathPrefix = extractPath(t);

      ManifestCatalog.Contract collect() {
        for (var m : t.getMethods()) addOperation(m);
        return contract.build();
      }

      void addOperation(Method m) {
        var op = new ManifestCatalog.Operation.Builder()
            .path(commonPathPrefix + extractPath(m))
            .name(m.getName())
            .returns(extractType(m.getReturnType(), m.getGenericReturnType()));

        for (var annotation : m.getAnnotations()) {
          var a = annotation.annotationType();
          if (a == GET.class) op.method(ManifestCatalog.Operation.Method.GET);
          else if (a == POST.class) op.method(ManifestCatalog.Operation.Method.POST);
          else if (a == PUT.class) op.method(ManifestCatalog.Operation.Method.PUT);
        }

        collectParameters(m, op);
        contract.addOperations(op.build());
      }

      private String extractPath(AnnotatedElement e) {
        return Optional.ofNullable(e.getAnnotation(Path.class)).map(Path::value).orElse("");
      }

      private void collectParameters(Method m, ManifestCatalog.Operation.Builder operation) {
        Annotation[][] parameterAnnotations = m.getParameterAnnotations();
        Type[] parameterTypes = m.getGenericParameterTypes();
        Class<?>[] rawParameterTypes = m.getParameterTypes();
        var parameters = m.getParameters();

        for (int i = 0; i < parameters.length; i++) {
          var param = new ManifestCatalog.Parameter.Builder();
          var kind = ManifestCatalog.Parameter.Kind.ENTITY;
          var name = parameters[i].getName();

          for (var annotation : parameterAnnotations[i]) {
            var a = annotation.annotationType();
            if (a == PathParam.class) {
              kind = ManifestCatalog.Parameter.Kind.PATH;
              name = ((PathParam) annotation).value();
            } else if (a == MatrixParam.class) {
              kind = ManifestCatalog.Parameter.Kind.MATRIX;
              name = ((MatrixParam) annotation).value();
            } else if (a == QueryParam.class) {
              kind = ManifestCatalog.Parameter.Kind.QUERY;
              name = ((QueryParam) annotation).value();
            } else if (a == HeaderParam.class) {
              kind = ManifestCatalog.Parameter.Kind.HEADER;
              name = ((HeaderParam) annotation).value();
            } else if (a == DefaultValue.class) {
              param.defaults(((DefaultValue) annotation).value());
            }
          }

          operation.addParameters(param
              .name(name)
              .kind(kind)
              .type(extractType(rawParameterTypes[i], parameterTypes[i]))
              .build());
        }
      }
    }

    return new Collecter().collect();
  }

  Types.Reference extractType(Manifest.Reference reference) {
    var t = References.key(reference).getTypeLiteral();
    return extractType(t.getRawType(), t.getType());
  }

  Types.Reference extractType(Type generic) {
    if (generic instanceof Class<?>) {
      return extractType((Class<?>) generic, generic);
    }
    if (generic instanceof ParameterizedType) {
      return extractType((Class<?>) ((ParameterizedType) generic).getRawType(), generic);
    }
    return intractable(generic.getTypeName());
  }

  Types.Reference extractType(Class<?> raw, Type generic) {
    var reference = SCALARS.get(raw);
    if (reference != null) return reference;
    if (raw == Optional.class) {
      return optional(extractType(getArguments(generic)[0]));
    }
    if (raw == List.class || raw == Vect.class) {
      return list(extractType(getArguments(generic)[0]));
    }
    if (raw == Set.class) {
      return set(extractType(getArguments(generic)[0]));
    }
    if (raw == Map.class) {
      var args = getArguments(generic);
      return map(extractType(args[0]), extractType(args[1]));
    }
    if (generic == raw) { // FIXME currently we don't encode generic substitute types, or other non-datatypes
      @Nullable var datatype = Datatypes.findDatatype(TypeToken.of(raw));
      if (datatype != null) {
        if (!structs.containsKey(raw)) {
          // cannot do computeIfAbsent -> concurrent modification exception
          Types.Struct struct = createStruct(raw, datatype);
          structs.put(raw, struct);
          return struct.reference();
        }
        return structs.get(raw).reference();
      }
    }
    return intractable(generic.getTypeName());
  }

  private Types.Struct createStruct(Class<?> raw, Datatype<?> datatype) {
    var builder = new Types.Struct.Builder();

    builder.reference(struct(raw));
    builder.inline(datatype.isInline());

    if (!datatype.cases().isEmpty()) {
      for (var c : datatype.cases()) {
        builder.addCases(createStruct(c.type().getRawType(), c));
      }
    }
    for (var f : datatype.features()) {
      Types.Reference t = extractType(f.type().getType());

      builder.addFeatures(new Types.Feature.Builder()
          .name(f.name())
          .type(t) // TODO Unwrap nullable from optional??
          .hasDefault(f.supportsInput() && f.omittableOnInput())
          // FIXME Do we need writeOnly/readOnly at el, why not the same as Datatype.Feature
          .writeOnly(f.supportsInput() && (!f.supportsOutput() || f.ignorableOnOutput()))
          .readOnly(f.supportsOutput() && !f.supportsInput())
          .nullable(t.kind() == Types.Reference.Kind.OPTIONAL)
          .build());
    }
    return builder.build();
  }

  private Type[] getArguments(Type generic) {
    return ((ParameterizedType) generic).getActualTypeArguments();
  }

  static final Types.Reference STRING = scalar("String");
  static final Types.Reference LONG = scalar("Long");
  static final Types.Reference INT = scalar("Int");
  static final Types.Reference DOUBLE = scalar("Double");
  static final Types.Reference FLOAT = scalar("Float");
  static final Types.Reference BOOL = scalar("Bool");
  static final Types.Reference URI = scalar("Uri");
  static final Types.Reference VOID = scalar("Void");

  static final Map<Class<?>, Types.Reference> SCALARS = ImmutableMap.<Class<?>, Types.Reference>builder()
      .put(String.class, STRING)
      .put(int.class, INT)
      .put(Integer.class, INT)
      .put(long.class, LONG)
      .put(Long.class, LONG)
      .put(double.class, DOUBLE)
      .put(Double.class, DOUBLE)
      .put(float.class, FLOAT)
      .put(Float.class, FLOAT)
      .put(boolean.class, BOOL)
      .put(Boolean.class, BOOL)
      .put(java.net.URI.class, URI)
      .put(Void.class, VOID)
      .put(void.class, VOID)
      .build();

  private static Types.Reference struct(Class<?> raw) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.STRUCT)
        .module(raw.getPackageName())
        .name(raw.getCanonicalName().replace(raw.getPackageName() + ".", ""))
        .build();
  }

  private static Types.Reference optional(Types.Reference element) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.OPTIONAL)
        .module("system")
        .name("Optional")
        .addArguments(element)
        .build();
  }

  private static Types.Reference list(Types.Reference element) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.LIST)
        .module("system")
        .name("List")
        .addArguments(element)
        .build();
  }

  private static Types.Reference set(Types.Reference element) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.SET)
        .module("system")
        .name("Set")
        .addArguments(element)
        .build();
  }

  private static Types.Reference scalar(String string) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.SCALAR)
        .module("system")
        .name(string)
        .build();
  }

  private static Types.Reference map(Types.Reference key, Types.Reference value) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.MAP)
        .module("system")
        .name("Map")
        .addArguments(key, value)
        .build();
  }

  private static Types.Reference contractType(Class<?> raw) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.CONTRACT)
        .module(raw.getPackageName())
        .name(raw.getCanonicalName().replace(raw.getPackageName() + ".", ""))
        .build();
  }

  private static Types.Reference recordType(Class<?> raw) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.RECORD)
        .module(raw.getPackageName())
        .name(raw.getCanonicalName().replace(raw.getPackageName() + ".", ""))
        .build();
  }

  private static Types.Reference intractable(String name) {
    return new Types.Reference.Builder()
        .kind(Types.Reference.Kind.INTRACTABLE)
        .module("*")
        .name(name)
        .build();
  }
}
