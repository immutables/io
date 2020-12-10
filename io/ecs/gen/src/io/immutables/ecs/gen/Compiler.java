package io.immutables.ecs.gen;

import io.immutables.Nullable;
import io.immutables.Source;
import io.immutables.Unreachable;
import io.immutables.codec.Codec;
import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.codec.Resolver;
import io.immutables.collect.Vect;
import io.immutables.ecs.def.Constraint;
import io.immutables.ecs.def.Definition;
import io.immutables.ecs.def.Model;
import io.immutables.ecs.def.Type;
import io.immutables.grammar.Symbol;
import io.immutables.grammar.TreeProduction;
import okio.Okio;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.squareup.moshi.JsonWriter;
import org.immutables.value.Value.Immutable;
import static java.util.Objects.requireNonNullElse;

class Compiler {
  static final Set<String> predefModuleNames = Set.of("ecs", "system");
  static final Type.Reference systemInline = Type.Reference.of("system", "Inline");
  static final Type.Reference ecsComponent = Type.Reference.of("ecs", "Component");
  static final Type.Reference ecsEntity = Type.Reference.of("ecs", "Entity");
  static final Type.Reference ecsSlug = Type.Reference.of("ecs", "Slug");

  static final Type.Reference httpGet = Type.Reference.of("http", "GET");
  static final Type.Reference httpPut = Type.Reference.of("http", "PUT");
  static final Type.Reference httpPost = Type.Reference.of("http", "POST");
  static final Type.Reference httpDelete = Type.Reference.of("http", "DELETE");
  static final Type.Reference httpHead = Type.Reference.of("http", "HEAD");

  final List<Src> sources = new ArrayList<>();
  final List<String> problems = new ArrayList<>();
  final ListMultimap<String, PerSource> moduleSources = ArrayListMultimap.create();
  final Map<String, Definition.Module> definedModules = new HashMap<>();

  void addPredef() {
    sources.add(Src.from(Resources.getResource(getClass(), "/io/immutables/ecs/def/system.ecs"), "system.ecs"));
    sources.add(Src.from(Resources.getResource(getClass(), "/io/immutables/ecs/def/ecs.ecs"), "ecs.ecs"));
  }

  void add(Src s) {
    sources.add(s);
  }

  boolean compile() {
    boolean ok = true;

    for (var s : sources) {
      PerSource perSource = new PerSource(s);
      if (perSource.tryRead()
          && perSource.tryParse()
          && perSource.tryDeclareModule()
          && perSource.tryDeclareImported()) {
        moduleSources.put(perSource.moduleName, perSource);
      }
      ok &= perSource.ok();
    }
    assert ok == problems.isEmpty();
    if (!ok) return false;

    var modulesForImports = new HashMap<String, ImportModule>();

    ImportResolver importResolver = new ImportResolver() {
      @Override
      public Optional<ImportModule> getModule(String name) {
        return Optional.ofNullable(modulesForImports.get(name));
      }
    };

    for (String moduleName : compilationOrder()) {
      var moduleBuilder = new Definition.Module.Builder().name(moduleName);
      for (var source : moduleSources.get(moduleName)) {
        var locallyKnownTypes = locallyKnownTypes(source, source.unit, importResolver, moduleName);
        buildModule(moduleName, source, source.unit, locallyKnownTypes, moduleBuilder);
        moduleBuilder.putSources(source.filename, source.content);
      }
      var module = moduleBuilder.build();
      definedModules.put(moduleName, module);
      modulesForImports.put(moduleName, toModuleForImports(module));
    }
    ok = problems.isEmpty();

    if (!ok) return false;

    for (String moduleName : definedModules.keySet()) {
      var module = definedModules.get(moduleName);
      module = module.withDefinitions(
          module.definitions()
              .map(m -> mergeInlineParameters(problems, m, definedModules)));
      definedModules.put(moduleName, module);
    }

    ok = problems.isEmpty();
    return ok;
  }

  private static ImportModule toModuleForImports(Definition.Module module) {
    return new ImportModule.Builder()
        .name(module.name())
        .addAllConcepts(module.definitions().only(Definition.OfConcept.class))
        .addAllTypes(module.definitions().only(Definition.OfType.class))
        .build();
  }

  private Vect<String> compilationOrder() {
    assert !moduleSources.isEmpty();
    var order = new LinkedHashSet<String>();
    var visiting = new HashSet<String>();
    visitOrderModules(order, visiting, moduleSources.keySet());
    //  System.err.println("ORDER!! " + Vect.from(order));
    return Vect.from(order);
  }

  private void visitOrderModules(Set<String> order, Set<String> visiting, Iterable<String> toVisit) {
    for (String module : toVisit) {
      if (!moduleSources.containsKey(module)) {
        // could be system or some special external or predef
        continue;
      }
      if (!order.contains(module)) {
        // Is it a correct way to detect cycles?
        if (!visiting.add(module)) {
          problems.add("Import cycle detected between in modules: " + visiting);
          continue;
        }
        for (var source : moduleSources.get(module)) {
          visitOrderModules(order, visiting, source.importedModules);
        }
        order.add(module);
        visiting.remove(module);
      }
    }
  }

  class PerSource implements Reporter {
    final Src src;
    final String filename;
    String content;
    SyntaxTerms terms;
    SyntaxProductions<SyntaxTrees.Unit> productions;
    SyntaxTrees.Unit unit;
    String moduleName;
    Set<String> importedModules;

    boolean ok = true; // this is per unit flag as opposed to shared check: problems.isEmpty()

    PerSource(Src src) {
      this.src = src;
      this.filename = src.toString();
    }

    boolean tryRead() {
      try {
        content = src.read();
        if (content.isEmpty() || !content.endsWith("\n")) {
          content += "\n";
        }
      } catch (IOException ex) {
        problems.add(filename + ": Cannot read source file\n" + ex);
        ok = false;
      }
      return ok;
    }

    boolean tryParse() {
      assert content != null;
      terms = SyntaxTerms.from(content.toCharArray());
      productions = SyntaxProductions.unit(terms);
      if (productions.ok()) {
        unit = productions.construct();
      } else {
        ok = false;
        problems.add(productions.messageForFile(filename));
      }
      return ok;
    }

    boolean tryDeclareModule() {
      assert unit != null;
      moduleName = extractModuleName(this, unit);
      return ok;
    }

    boolean tryDeclareImported() {
      assert moduleName != null;
      importedModules = importedModules(unit);
      return ok;
    }

    @Override
    public void problem(TreeProduction<?> production, String message, String hint) {
      ok = false;

      var range = terms.rangeInclusive(
          production.termBegin(),
          production.termEnd()).withinLine();

      problems.add(new Source.Problem(
          filename,
          terms.source(),
          terms.lines(),
          range,
          message,
          hint).toString());
    }

    @Override
    public boolean ok() {
      return ok;
    }
  }

  interface Reporter {
    void problem(TreeProduction<?> production, String message, String hint);
    boolean ok();
  }

  interface ImportResolver {
    Optional<ImportModule> getModule(String name);
  }

  @Immutable
  interface ImportModule {
    String name();
    Vect<Definition.OfType> types();
    Vect<Definition.OfConcept> concepts();
    Vect<Definition.OfContract> interfaces();

    class Builder extends ImmutableImportModule.Builder {}
  }

  @Immutable
  interface LocallyKnownTypes {
    Map<String, Type.Reference> types();

    class Builder extends ImmutableLocallyKnownTypes.Builder {}
  }

  private static void printModule(Definition.Module module) throws IOException {
    Resolver resolver = Codecs.builtin().toResolver();
    JsonWriter writer = JsonWriter.of(Okio.buffer(Okio.sink(System.err)));
    writer.setIndent("  ");
    Codec.Out out = OkJson.out(writer);
    resolver.get(Definition.Module.class).encode(out, module);
    writer.flush();
  }

  static String extractModuleName(Reporter reporter, SyntaxTrees.Unit unit) {
    var moduleNameSlot = new AtomicReference<String>();
    var alreadyComplainedModuleMissing = new AtomicBoolean(false);

    new SyntaxTrees.Visitor() {
      @Override
      public void caseImportDeclaration(SyntaxTrees.ImportDeclaration i) {
        if (moduleNameSlot.getPlain() == null) {
          reporter.problem(i, "Missing module declaration before",
              "Should be at the top of compilation unit before import declarations");
          alreadyComplainedModuleMissing.setPlain(true);
        }
      }

      @Override
      public void caseModuleDeclaration(SyntaxTrees.ModuleDeclaration m) {
        @Nullable String name = moduleNameSlot.getPlain();
        if (name != null) {
          reporter.problem(m, "Duplicate module declaration", "Was found <" + name + ">");
        }
        moduleNameSlot.setPlain(moduleOf(m.name()));
      }

      @Override
      public void caseTypeDeclaration(SyntaxTrees.TypeDeclaration t) {
        if (moduleNameSlot.getPlain() == null) {
          reporter.problem(t, "Missing module declaration before",
              "Should be at the top of compilation unit before type declarations");
          alreadyComplainedModuleMissing.setPlain(true);
        }
      }
    }.caseUnit(unit);

    if (moduleNameSlot.getPlain() == null && !alreadyComplainedModuleMissing.getPlain()) {
      reporter.problem(unit, "Missing module declaration",
          "Should be at the top of compilation unit");
    }

    return requireNonNullElse(moduleNameSlot.getPlain(), "<undeclared>");
  }

  static Set<String> importedModules(SyntaxTrees.Unit unit) {
    var imported = new HashSet<String>();

    new SyntaxTrees.Visitor() {
      @Override
      public void caseImportDeclaration(SyntaxTrees.ImportDeclaration i) {
        imported.add(moduleOf(i.name()));
      }
    }.caseUnit(unit);

    return Set.copyOf(imported);
  }

  static LocallyKnownTypes locallyKnownTypes(
      Reporter reporter,
      SyntaxTrees.Unit unit,
      ImportResolver importResolver,
      String moduleName) {

    var localNameSet = new HashSet<String>();
    var importedNames = new HashMap<String, Type.Reference>();

    var locallyBuilder = new LocallyKnownTypes.Builder();

    new SyntaxTrees.Visitor() {
      @Override
      public void caseImportDeclaration(SyntaxTrees.ImportDeclaration i) {
        var maybeModule = importResolver.getModule(moduleOf(i.name()));

        if (maybeModule.isPresent()) {
          var m = maybeModule.get();

          for (var t : m.types()) {
            if (localNameSet.contains(t.name())) {
              reporter.problem(i,
                  "Imported type from `" + m.name() + "` conflicts with already defined type: " + t.name(), "");
            } else if (importedNames.containsKey(t.name())) {
              reporter.problem(i,
                  "Imported type `" + m.name() + "` conflicts with another import: " + importedNames.get(t.name()), "");
            } else {
              var ref = Type.Reference.of(m.name(), t.name());
              importedNames.put(t.name(), ref);
              locallyBuilder.putTypes(t.name(), ref);
              //locallyBuilder.putImportedTypes(ref, t);
            }
          }
          for (var c : m.concepts()) {
            if (localNameSet.contains(c.name())) {
              reporter.problem(i,
                  "Imported concept from `" + m.name() + "` conflicts with already defined type: " + c.name(), "");
            } else if (importedNames.containsKey(c.name())) {
              reporter.problem(i,
                  "Imported concept `" + m.name() + "` conflicts with another import: " + importedNames.get(c.name())
                  , "");
            } else {
              var ref = Type.Reference.of(m.name(), c.name());
              importedNames.put(c.name(), ref);
              locallyBuilder.putTypes(c.name(), ref);
              //locallyBuilder.putImportedConcepts(ref, c);
            }
          }
          for (var n : m.interfaces()) {
            if (localNameSet.contains(n.name())) {
              reporter.problem(i,
                  "Imported interface from `" + m.name() + "` conflicts with already defined type: " + n.name(), "");
            } else if (importedNames.containsKey(n.name())) {
              reporter.problem(i,
                  "Imported interface `" + m.name() + "` conflicts with another import: " + importedNames.get(n.name())
                  , "");
            } else {
              var ref = Type.Reference.of(m.name(), n.name());
              importedNames.put(n.name(), ref);
              locallyBuilder.putTypes(n.name(), ref);
              //locallyBuilder.putImportedConcepts(ref, c);
            }
          }
        } else reporter.problem(i, "Cannot find module " + moduleOf(i.name()),
            "It is not declared or spelled differently");
      }

      @Override
      public void caseConceptDeclaration(SyntaxTrees.ConceptDeclaration c) {
        onNamedDeclaration(c, c.name().toString());
      }

      @Override
      public void caseTypeDeclaration(SyntaxTrees.TypeDeclaration t) {
        onNamedDeclaration(t, t.name().toString().replace("SYSTEMKEYWORD", ""));
      }

      @Override public void caseContractDeclaration(SyntaxTrees.ContractDeclaration n) {
        onNamedDeclaration(n, n.name().toString());
      }

      private void onNamedDeclaration(TreeProduction<SyntaxTrees> p, String name) {
        if (importedNames.containsKey(name)) {
          reporter.problem(p,
              "Local declaration " + name + " have a name conflicting with imported " + importedNames.get(name),
              "Type or concept of the same name was already declared in compilation unit");
        } else if (!localNameSet.add(name)) {
          reporter.problem(p, "Cannot redeclare type " + name,
              "Type or concept of the same name was already declared in compilation unit");
        } else {
          locallyBuilder.putTypes(name, Type.Reference.of(moduleName, name));
        }
      }
    }.caseUnit(unit);

    return locallyBuilder.build();
  }

	private static String moduleOf(Vect<Symbol> name) {
  	return name.join(".");
	}

	static void buildModule(
      String moduleName,
      Reporter reporter,
      SyntaxTrees.Unit unit,
      LocallyKnownTypes localTypeNames,
      Definition.Module.Builder moduleBuilder) {

    var moduleScope = new ModuleScope(moduleName, reporter, localTypeNames);

    new SyntaxTrees.Visitor() {
      @Override
      public void caseConceptDeclaration(SyntaxTrees.ConceptDeclaration conceptDecl) {

        var name = conceptDecl.name().toString();
        var conceptBuilder = new Definition.ConceptDefinition.Builder()
            .module(moduleName)
						.comment(getComment(conceptDecl.comment()))
            .name(name);

        var signatureScope = new SignatureScope(moduleScope, name);
        for (Symbol s : conceptDecl.typeParameter()) {
          @Nullable var v = signatureScope.introduceParameter(s.toString());
          if (v != null) conceptBuilder.addParameters(v);
          else reporter.problem(conceptDecl, "Duplicate type parameter: " + s, "");
        }

        for (var constraint : conceptDecl.constraint()) {
          if (constraint instanceof SyntaxTrees.TypeConstraintConception) {
            var concept = ((SyntaxTrees.TypeConstraintConception) constraint).concept();
            conceptBuilder.addConstraints(extractConcept(signatureScope, concept));
          }
        }

        if (reporter.ok()) moduleBuilder.addDefinitions(conceptBuilder.build());
      }

      @Override
      public void caseContractDeclaration(SyntaxTrees.ContractDeclaration interfaceDecl) {
        var name = interfaceDecl.name().toString();
        var interfaceBuilder = new Definition.ContractDefinition.Builder()
            .module(moduleName)
						.comment(getComment(interfaceDecl.comment()))
            .name(name);

        var signatureScope = new SignatureScope(moduleScope, name);
        for (Symbol s : interfaceDecl.typeParameter()) {
          @Nullable var v = signatureScope.introduceParameter(s.toString());
          if (v != null) interfaceBuilder.addParameters(v);
          else reporter.problem(interfaceDecl, "Duplicate type parameter: " + s, "");
        }

        var features = interfaceDecl.features()
            .stream()
            .flatMap(f -> f.element().stream())
            .collect(Vect.to());

        for (var f : features) {
          if (f instanceof SyntaxTrees.FeatureNamed) {
            interfaceBuilder.addFeatures(extractFeature(signatureScope, (SyntaxTrees.FeatureNamed) f));
          }
        }

        for (var constraint : interfaceDecl.constraint()) {
          if (constraint instanceof SyntaxTrees.TypeConstraintConception) {
            var concept = ((SyntaxTrees.TypeConstraintConception) constraint).concept();
            interfaceBuilder.addConstraints(extractConcept(signatureScope, concept));
          }
        }

        if (reporter.ok()) moduleBuilder.addDefinitions(interfaceBuilder.build());
      }

      private Type.Feature extractFeature(SignatureScope scope, SyntaxTrees.FeatureNamed feature) {
        var name = feature.name().toString();
        var featureScope = new SignatureScope(scope, name);

        var builder = new Type.Feature.Builder().name(name);

        for (Symbol s : feature.typeParameter()) {
          @Nullable var v = featureScope.introduceParameter(s.toString());
          if (v != null) builder.addParameters(v);
          else reporter.problem(feature, "Duplicate type parameter: " + s, "");
        }

        for (var constraint : feature.constraint()) {
          if (constraint instanceof SyntaxTrees.TypeConstraintConception) {
            var concept = ((SyntaxTrees.TypeConstraintConception) constraint).concept();
            builder.addConstraints(extractConcept(featureScope, concept));
          }
        }

        return builder.addAllInParameters(buildConstructor(reporter, featureScope, feature.input()).parameters())
            .out(feature.output().map(ret -> extractReturnType(scope, ret)).orElse(Type.Empty.of()))
						.comment(getComment(feature.comment()))
            .build();
      }

      private Type extractReturnType(SignatureScope scope, SyntaxTrees.ReturnType ret) {
        return ret.component().<Type>when()
            .empty(Type.Empty::of)
            .single(rt -> extractType.match(rt, scope))
            .otherwise(rt -> Type.Product.of(rt.map(r -> extractType.match(r, scope))));
      }

			@Override
			public void caseEntityDeclaration(SyntaxTrees.EntityDeclaration entityDecl) {
      	var name = entityDecl.name().toString();

				var parameter = entityDecl.constructor();

				if (parameter.isEmpty()) {
					reporter.problem(entityDecl, "No constructor parameter for an Entity" + name,
							"Entity definition should have one inline constructor parameter of scalar type like: (code String)");
					return;
				}

				if (parameter.get().components().size() > 1) {
					reporter.problem(entityDecl, "More than one constructor parameter for an Entity" + name,
							"Entity definition should have one inline constuctor parameter of scalar type like: (code String)");
					return;
				}

				var signatureScope = new SignatureScope(moduleScope, name);

				var e = new Definition.EntityDefinition.Builder()
						.name(name)
						.module(moduleName)
						.comment(getComment(entityDecl.comment()))
						.constructor(buildConstructor(reporter, signatureScope, parameter));

				for (var facet : entityDecl.facet()) {
					var facetName = facet.name().toString();

					var comment = getComment(facet.comment());
					var componentType = extractType.match(facet.type(), signatureScope);

					if (facet.slug().isPresent()) {
						var slug = facet.slug().get();
						var slugComment = getComment(slug.comment());
						var slugName = slug.name().toString();
						var slugType = extractType.match(slug.type(), signatureScope);

						var slugParameter = Definition.NamedParameter.of(slugName, slugType)
								.withComment(slugComment);

						e.addFeatures(Type.Feature.of(facetName, Vect.of(slugParameter), componentType)
								.withComment(comment));
					} else {
						e.addFeatures(Type.Feature.field(facetName, componentType).withComment(comment));
					}
				}

				moduleBuilder.addDefinitions(e.build());
			}

			@Override
      public void caseTypeDeclaration(SyntaxTrees.TypeDeclaration typeDecl) {
        @Nullable var topConstructor = typeDecl.constructor().orElse(null);

        var t = new Definition.DataTypeDefinition.Builder();
        var name = typeDecl.name().toString().replace("SYSTEMKEYWORD", "");
        t.name(name).module(moduleName).comment(getComment(typeDecl.comment()));

        if (topConstructor instanceof SyntaxTrees.ConstructorCases) {
          t.hasCases(true);

          var signatureScope = new SignatureScope(moduleScope, name);
          for (Symbol s : typeDecl.typeParameter()) {
            @Nullable var v = signatureScope.introduceParameter(s.toString());
            if (v != null) t.addParameters(v);
            else reporter.problem(typeDecl, "Duplicate type parameter: " + s, "");
          }

          for (var constructorCase : ((SyntaxTrees.ConstructorCases) topConstructor).cases()) {
            Optional<SyntaxTrees.Parameter> parameter = constructorCase.constructor();
            var constructor = buildConstructor(reporter, signatureScope, parameter);
            t.putConstructors(constructorCase.name().toString(), constructor);
          }

          for (var constraint : typeDecl.constraint()) {
            if (constraint instanceof SyntaxTrees.TypeConstraintConception) {
              var concept = ((SyntaxTrees.TypeConstraintConception) constraint).concept();
              t.addConstraints(extractConcept(signatureScope, concept));
            }
          }
        } else if (topConstructor instanceof SyntaxTrees.ConstructorParameter || topConstructor == null) {
          t.hasCases(false);

          var signatureScope = new SignatureScope(moduleScope, name);
          for (Symbol s : typeDecl.typeParameter()) {
            @Nullable var v = signatureScope.introduceParameter(s.toString());
            if (v != null) t.addParameters(v);
            else reporter.problem(typeDecl, "Duplicate type parameter: " + s, "");
          }

          var constructor = buildConstructor(
              reporter,
              signatureScope,
              Optional.ofNullable(topConstructor)
                  .map(cp -> ((SyntaxTrees.ConstructorParameter) cp).input()));

          t.putConstructors(name, constructor);

          for (var constraint : typeDecl.constraint()) {
            if (constraint instanceof SyntaxTrees.TypeConstraintConception) {
              var concept = ((SyntaxTrees.TypeConstraintConception) constraint).concept();
              t.addConstraints(extractConcept(signatureScope, concept));
            }
          }
        }

        if (reporter.ok()) moduleBuilder.addDefinitions(t.build());
      }

      private Constraint.Concept extractConcept(SignatureScope signatureScope, SyntaxTrees.TypeReferenceNamed concept) {
        Type type = extractType.match(concept, signatureScope);
/**
        type.accept(new Type.Visitor<Void, Definition.OfConcept>() {
          @Override public Definition.OfConcept reference(Type.Reference d, Void in) {
            var module = modules.get(reference.module());
            var definition = module.byName().get(reference.name());
          return null;
          }

          @Override public Definition.OfConcept parameterized(Type.Parameterized d, Void in) {
            @Nullable Definition.OfConcept c = reference(d.reference(), in);
            if (c != null) {
              if (c.parameters().size() != d.arguments().size()) {
                reporter.problem(concept,
                    "Arguments of " + type + " do not match parameters " + c.parameters().join(",", "<", ">"), "");
              }
            }
            return c;
          }

          @Override public Definition.OfConcept otherwise(Type t, Void in) {
            reporter.problem(concept, "Cannot form concept", "Type " + type + " doesn't represent valid concept");
            return null;
          }
        }, null);
*/
        return Constraint.Concept.of(type);
      }
    }.caseUnit(unit);
  }

	private static String getComment(Vect<Symbol> comment) {
		return comment.map(s -> s.toString().substring(2).trim()).join("\n");
	}

	static Definition.Constructor buildConstructor(
      Reporter reporter,
      SignatureScope scope,
      Optional<? extends SyntaxTrees.Parameter> parameter) {

    var constructorBuilder = new Definition.Constructor.Builder();

    if (parameter.isEmpty()) {
      return constructorBuilder.takesRecord(false).build();
    }

    var p = parameter.get();

    var v = new SyntaxTrees.Visitor() {
      final Set<String> parameterNames = new HashSet<>();
      int parameterCounter = 0;

      private String syntheticName() {
        // TODO maybe extract better names such as derived from letters
        // of nominal types (type name, variable, like `t` for `T`) and
        // use counter only as last resort
        for (int inc = 0; parameterCounter + inc >= 0 /* won't overflow */; inc++) {
          String n = "arg" + (parameterCounter + inc);
          if (parameterNames.add(n)) return n;
        }
        throw Unreachable.wishful();
      }

      private String uniqueName(TreeProduction<SyntaxTrees> production, Symbol name) {
        String n = name.toString();
        if (!parameterNames.add(n)) {
          reporter.problem(production, "Duplicate parameter name " + n,
              "All parameter names should be different");
        }
        return n;
      }

      @Override
      public void caseUnnamedParameter(SyntaxTrees.UnnamedParameter p) {
        var t = p.type();
        constructorBuilder.addParameters(Definition.NamedParameter.of(
            syntheticName(),
            extractType.match(t, scope)).withHasSyntheticName(true));

        parameterCounter++;
      }

      @Override
      public void caseNamedParametersBind(SyntaxTrees.NamedParametersBind p) {
        for (Symbol s : p.name()) {
          var t = p.type();

          // System.err.println("NPB " + scope + "  " + scope.getType("Entity", p));

          var constraints = p.constraint()
              .only(SyntaxTrees.TypeConstraintConception.class)
              .map(c -> Constraint.Concept.of(extractType.match(c.concept(), scope)));

          var parameter = Definition.NamedParameter.of(
              uniqueName(p, s),
              extractType.match(t, scope)).withConstraints(constraints);

          constructorBuilder.addParameters(parameter);

          parameterCounter++;
        }
      }

      @Override
      public void caseNamedParameters(SyntaxTrees.NamedParameters p) {
        for (Symbol s : p.name()) {
          var t = p.type();
          constructorBuilder.addParameters(Definition.NamedParameter.of(
              uniqueName(p, s),
              extractType.match(t, scope)));

          parameterCounter++;
        }
      }

      @Override
      public void caseParameterRecord(SyntaxTrees.ParameterRecord record) {
        for (var f : record.fields()) {
          caseNamedParametersBind(f);
        }
        for (var t : record.inline()) {
          constructorBuilder.addInlines(extractType.match(t, scope));
        }
      }
    };

    if (p instanceof SyntaxTrees.ParameterProduct) {
      constructorBuilder.takesRecord(false);
      v.caseParameterProduct((SyntaxTrees.ParameterProduct) p);
    } else if (p instanceof SyntaxTrees.ParameterRecord) {
      constructorBuilder.takesRecord(true);
      v.caseParameterRecord((SyntaxTrees.ParameterRecord) p);
    } else throw Unreachable.exhaustive();

    return constructorBuilder.build();
  }

  static Definition mergeInlineParameters(
      Collection<String> problems,
      Definition definition,
      Map<String, Definition.Module> modules) {
    if (definition instanceof Definition.DataTypeDefinition) {
      var dt = (Definition.DataTypeDefinition) definition;
      return dt.withConstructors(
          Maps.transformValues(dt.constructors(),
              c -> mergeInlineParameters(problems, c, modules)));
    }
    if (definition instanceof Definition.EntityDefinition) {
    	var ed = (Definition.EntityDefinition) definition;
			return ed.withConstructor(mergeInlineParameters(problems, ed.constructor(), modules));
		}
    return definition;
  }

  static Definition.Constructor mergeInlineParameters(
      Collection<String> problems,
      Definition.Constructor constructor,
      Map<String, Definition.Module> modules) {

    if (constructor.takesRecord()) {
      var parameters = new LinkedHashMap<String, Definition.NamedParameter>();

      for (Type t : constructor.inlines()) {
        t.accept(new Type.Visitor<Void, Void>() {
          @Override
          public Void reference(Type.Reference reference, Void in) {
            var module = modules.get(reference.module());
            var definition = module.byName().get(reference.name());

            datatype:
            if (definition instanceof Definition.DataTypeDefinition) {
              var datatype = (Definition.DataTypeDefinition) definition;
              // c.mergedParameters(); !!!!! OMG how to arrange that, recursion or queue?

              if (!datatype.constructor().takesRecord()) {
                problems.add("Embedding require record constructor, a not positional production "
                    + datatype.constructor().toType());
                break datatype;
              }

              if (!datatype.parameters().isEmpty()) {
                problems.add("Type " + reference + " has type parameters but no type arguments are provided.");
                break datatype;
              }

              for (var p : datatype.constructor().parameters()) {
                if (parameters.containsKey(p.name())) {
                  problems.add("Duplicate attribute '" + p.name()
                      + "' while inlining constructor " + reference);
                }
                parameters.put(p.name(), p);
              }
            } else {
              problems.add("Cannot embed non-datatype: " + reference
                  + ". Concepts and Case types cannot be directly decomposed to a set of parameters.");
            }

            return in;
          }

          @Override
          public Void parameterized(Type.Parameterized parameterized, Void in) {
            var module = modules.get(parameterized.reference().module());
            var definition = module.byName().get(parameterized.reference().name());

            datatype:
            if (definition instanceof Definition.DataTypeDefinition) {
              var datatype = (Definition.DataTypeDefinition) definition;

              if (!datatype.constructor().takesRecord()) {
                problems.add("Embedding require record constructor, not a positional production "
                    + datatype.constructor().toType());
                break datatype;
              }
              // c.mergedParameters(); !!!!! OMG how to arrange that, recursion or queue?

              if (datatype.parameters().size() != parameterized.arguments().size()) {
                problems.add("Wrong number of type arguments " + parameterized);
                break datatype;
              }

              TypeResolver resolver = new TypeResolver(datatype.parameters(), parameterized.arguments());

              for (var p : datatype.constructor().parameters()) {
                if (parameters.containsKey(p.name())) {
                  problems.add("Duplicate attribute '" + p.name()
                      + "' while inlining constructor " + parameterized.reference());
                }
                parameters.put(p.name(),
                    Definition.NamedParameter.of(p.name(),
                        resolver.resolve(p.type())));
              }
            } else {
              problems.add("Cannot embed non-datatype: " + parameterized.reference()
                  + ". Concepts and Case types cannot be directly decomposed to a set of parameters.");
            }

            return in;
          }
        }, null);
      }

      for (Definition.NamedParameter p : constructor.parameters()) {
        if (parameters.containsKey(p.name())) {
          problems.add("Duplicate attribute '" + p.name() + "' after inlining constructors");
        }
        parameters.put(p.name(), p);
      }

      return constructor.withMergedParameters(parameters.values());
    }
    return constructor.withMergedParameters(constructor.parameters());
  }

  static class TypeResolver implements Type.Visitor<Void, Type> {
    private final Vect<Type.Variable> parameters;
    private final Vect<Type> arguments;

    TypeResolver(Vect<Type.Variable> parameters, Vect<Type> arguments) {
      this.parameters = parameters;
      this.arguments = arguments;
    }

    @Override
    public Type product(Type.Product p, Void in) {
      return Type.Product.of(
          p.components().map(this::resolve));
    }

    @Override
    public Type parameterized(Type.Parameterized d, Void in) {
      return Type.Parameterized.of(
          d.reference(),
          d.arguments().map(this::resolve));
    }

    @Override
    public Type variable(Type.Variable v, Void in) {
      for (int i = 0; i < parameters.size(); i++) {
        if (parameters.get(i).equals(v)) {
          return arguments.get(i);
        }
      }
      return v;
    }

    @Override
    public Type otherwise(Type t, Void in) {
      return t;
    }

    Type resolve(Type t) {
      return t.accept(this, null);
    }
  }

  static abstract class Scope {
    abstract Type getType(String name, TreeProduction<SyntaxTrees> production);
    abstract Type getUnresolved(String name, TreeProduction<SyntaxTrees> v);
  }

  static class ModuleScope extends Scope {
    private final String name;
    final LocallyKnownTypes locally;
    final Reporter reporter;

    ModuleScope(String name, Reporter reporter, LocallyKnownTypes locally) {
      this.name = name;
      this.locally = locally;
      this.reporter = reporter;
    }

    @Override
    Type getType(String name, TreeProduction<SyntaxTrees> production) {
      var resolved = locally.types().get(name);
      if (resolved != null) return resolved;
      return getUnresolved(name, production);
    }

    @Override
    Type getUnresolved(String name, TreeProduction<SyntaxTrees> v) {
      reporter.problem(v, "Unknown type " + name, "Check spelling or imported modules");
      return Type.Unresolved.of(name);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  static class SignatureScope extends Scope {
    private final String name;
    final Scope parent;
    final List<Type.Variable> parameters = new ArrayList<>();

    SignatureScope(Scope parent, String name) {
      this.parent = parent;
      this.name = name;
    }

    @Nullable
    Type.Variable introduceParameter(String name) {
      for (Type.Variable p : parameters) {
        if (p.name().equals(name)) return null;
      }
      var v = Type.Variable.of(name);
      parameters.add(v);
      return v;
    }

    @Override
    Type getType(String name, TreeProduction<SyntaxTrees> production) {
      for (Type.Variable p : parameters) {
        if (p.name().equals(name)) return p;
      }
      return parent.getType(name, production);
    }

    @Override
    Type getUnresolved(String name, TreeProduction<SyntaxTrees> v) {
      return parent.getUnresolved(name, v);
    }

    @Override
    public String toString() {
      return parent + ":" + name + Vect.from(parameters).join(",", "<", ">");
    }
  }

  private static final SyntaxTrees.Matcher<Scope, Type> extractType = new SyntaxTrees.Matcher<>() {
    @Override
    public Type caseTypeReferenceNamed(SyntaxTrees.TypeReferenceNamed v, Scope scope) {
      var resolvedType = scope.getType(v.name().toString(), v);
      var args = v.argument();

      if (!args.isEmpty() && resolvedType instanceof Type.Reference) {
        return Type.Parameterized.of((Type.Reference) resolvedType, args.map(t -> this.match(t, scope)));
      }

      return resolvedType;
    }

    @Override
    public Type caseTypeReferenceOptional(SyntaxTrees.TypeReferenceOptional v, Scope scope) {
      return Type.Option.of(this.match(v.component(), scope));
    }

    @Override
    public Type caseTypeReferenceArray(SyntaxTrees.TypeReferenceArray v, Scope scope) {
      return Type.Array.of(this.match(v.component(), scope));
    }

    @Override
    public Type caseTypeReferenceSetn(SyntaxTrees.TypeReferenceSetn v, Scope scope) {
      return Type.Setn.of(this.match(v.component(), scope));
    }

    @Override public Type caseTypeReferenceKeyword(SyntaxTrees.TypeReferenceKeyword v, Scope scope) {
      return scope.getType(v.name().toString(), v);
    }

    @Override
    public Type caseTypeReferenceProduct(SyntaxTrees.TypeReferenceProduct v, Scope scope) {
      return v.component().<Type>when()
          .empty(Type.Empty::of)
          .single(e -> this.match(e, scope))
          .otherwise(vs -> Type.Product.of(vs.map(t -> this.match(t, scope))));
    }

    @Override
    protected Type fallback(TreeProduction<SyntaxTrees> v, Scope scope) {
      // TODO in general we need a way to easily get source text from
      // productionIndex / (termBegin, termEnd)
      // right now we cannot get it easily unless Productions/Terms object is passed all along
      return scope.getUnresolved(v.toString(), v);
    }
  };

  interface Src {
    String read() throws IOException;

    static Src from(Path path) {
      return new Src() {
        @Override
        public String read() throws IOException {
          return Files.readString(path);
        }

        @Override
        public String toString() {
          return path.getFileName().toString();
        }
      };
    }

    static Src from(URL url, String filename) {
      return new Src() {
        @Override
        public String read() throws IOException {
          return Resources.toString(url, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
          return filename;
        }
      };
    }
  }

  Model extractModel() {
    var modules = definedModules.values()
					.stream()
					.filter(c -> !predefModuleNames.contains(c.name()))
					.collect(Collectors.toList());

    Model.Builder b = new Model.Builder();

    for (var m : modules) {
			for (var entity : m.definitions().only(Definition.EntityDefinition.class)) {
				b.addDataTypes(Model.DataType.of(m, toDataType(entity)));
				b.addEntities(Model.Entity.of(m, entity));

				for (var f : entity.features()) {
					var entityParameter = Definition.NamedParameter.of(
							CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, entity.name()),
							Type.Reference.of(m.name(), entity.name()));

					Model.Component.Builder component = new Model.Component.Builder()
							.module(m)
							.name(entity.name() + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, f.name()))
							.entity(entityParameter)
							.component(Definition.NamedParameter.of(f.name(), f.out()));

					var in = f.inParameters();
					if (in.size() == 1) {
						component.slug(in.get(0));
					}
					b.addComponents(component.build());
				}
			}

      for (var type : m.definitions().only(Definition.DataTypeDefinition.class)) {
        var reference = Type.Reference.of(m.name(), type.name());
        if (isComponent(m, type, reference)) {
          produceComponent(b, m, type, reference);
        } else {
          b.addDataTypes(Model.DataType.of(m, type));
        }
      }

      for (var type : m.definitions().only(Definition.ContractDefinition.class)) {
        b.addContracts(Model.Contract.of(m, type));
      }
    }

    return b.build();
  }

	private Definition.DataTypeDefinition toDataType(Definition.EntityDefinition entity) {
		var builder = new Definition.DataTypeDefinition.Builder()
				.module(entity.module())
				.name(entity.name())
				.comment(entity.comment())
				.hasCases(false)
				.putConstructors(entity.name(), entity.constructor())
				.addAllConstraints(entity.constraints());

		if (!entity.hasConcept(ecsEntity)) {
			builder.addConstraints(Constraint.Concept.of(ecsEntity));
		}

		if (!entity.hasConcept(systemInline)) {
			builder.addConstraints(Constraint.Concept.of(systemInline));
		}

		return builder.build();
	}

	Optional<Definition.DataTypeDefinition> findDatatype(Type type) {
    return type.accept(new Type.Visitor<Void, Optional<Definition.DataTypeDefinition>>() {
      @Override public Optional<Definition.DataTypeDefinition> reference(Type.Reference ref, Void in) {
        @Nullable var m = definedModules.get(ref.module());
        if (m != null) {
          @Nullable var def = m.byName().get(ref.name());
          if (def instanceof Definition.DataTypeDefinition) {
            return Optional.of((Definition.DataTypeDefinition) def);
          }
        }
        return Optional.empty();
      }

      @Override public Optional<Definition.DataTypeDefinition> parameterized(Type.Parameterized d, Void in) {
        return reference(d.reference(), in);
      }

      @Override public Optional<Definition.DataTypeDefinition> otherwise(Type t, Void in) {
        return Optional.empty();
      }
    }, null);
  }

  void produceComponent(
      Model.Builder modelBuilder,
      Definition.Module module,
      Definition.DataTypeDefinition definition,
      Type.Reference type) {

    Model.Component.Builder builder = new Model.Component.Builder()
        .module(module)
        .name(definition.name());

    boolean wasEntity = false;
    boolean wasSlug = false;
    boolean wasComponent = false;

    for (Definition.NamedParameter p : definition.constructor().mergedParameters()) {
      boolean isEntity = hasConcept(p.constraints(), ecsEntity);
      boolean isSlug = hasConcept(p.constraints(), ecsSlug);

      if (isEntity && isSlug) {
        problems.add("Field `" + p.name() + "` of component " + type + " cannot be both ::Entity and ::Slug");
        wasEntity = wasSlug = true;
      } else if (isEntity) {
        if (wasEntity) {
          problems.add("Only one field of component " + type + " can be ::Entity." +
              " Duplicate entity field: `" + p.name() + "`");
        } else {
          Optional<Definition.DataTypeDefinition> datatype = findDatatype(p.type());

          if (datatype.isEmpty() || !datatype.get().hasConcept(systemInline)) {
            problems.add("::Entity type is not an inline datatype: " + p.type());
          }
          builder.entity(p);
          wasEntity = true;
        }
      } else if (isSlug) {
        if (wasSlug) {
          problems.add("Only one field of component " + type + " can be ::Slug." +
              " Duplicate slug field: `" + p.name() + "`");
        } else {
          builder.slug(p);
          wasSlug = true;
        }
      } else {
        if (wasComponent) {
          problems.add("Component " + type + " has more that one component field. As of now it is not supported."
              + " Duplicate field: `" + p.name() + "`");
        } else {
          builder.component(p);
          wasComponent = true;
        }
      }
    }

    if (!wasComponent) {
      problems.add("No component field defined by component defined by " + type + ". Need a single unannotated field");
    }

    if (!wasEntity) {
      problems.add("Component " + type + " must have entity field ::Entity");
    }

    if (problems.isEmpty()) {
      modelBuilder.addComponents(builder.build());
    }
  }

  static boolean hasConcept(Vect<Constraint> constraints, Type type) {
    return constraints
        .only(Constraint.Concept.class)
        .any(c -> c.type().equals(type));
  }

  boolean isComponent(
      Definition.Module module,
      Definition.DataTypeDefinition definition,
      Type type) {
    if (hasConcept(definition.constraints(), ecsComponent)) {
      if (definition.hasCases() || !definition.constructor().takesRecord()) {
        problems.add("Component definition " + type + " cannot be case type, but must have simple record constructor." +
            " The the component value field can be of any datatype.");
      }
      return problems.isEmpty();
    }
    return false;
  }

  public static void main(String... args) throws IOException {
    var sourceName = "/io/immutables/ecs/gen/sample3.ecs";
    var filename = "sample3.ecs";

    Src src = Src.from(Resources.getResource(Compiler.class, sourceName), filename);

    Compiler compiler = new Compiler();
    compiler.add(src);
 		compiler.addPredef();
    if (compiler.compile()) {
      for (var source : compiler.moduleSources.values()) {
        System.out.println(source.productions.show());
        System.out.println(source.productions.construct());
      }
      for (var m : compiler.definedModules.values()) {
        printModule(m);
      }
      Model model = compiler.extractModel();
      System.err.println("COMPONENTS!!!:");
      for (var c : model.components()) {
        System.err.println(c);
      }
      System.err.flush();
    }
    if (!compiler.problems.isEmpty()) {
      for (String p : compiler.problems) {
        System.err.println(p);
      }
      var terms = SyntaxTerms.from(src.read().toCharArray());
      var productions = SyntaxProductions.unit(terms);
      //System.err.println(terms.show());
      System.err.println(productions.show());
    }
  }
}
