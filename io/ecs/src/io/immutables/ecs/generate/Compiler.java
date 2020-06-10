package io.immutables.ecs.generate;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.squareup.moshi.JsonWriter;
import io.immutables.Nullable;
import io.immutables.Source;
import io.immutables.Unreachable;
import io.immutables.codec.Codec;
import io.immutables.codec.Codecs;
import io.immutables.codec.OkJson;
import io.immutables.codec.Resolver;
import io.immutables.collect.Vect;
import io.immutables.ecs.Constraint;
import io.immutables.ecs.Definition;
import io.immutables.ecs.Definition.NamedParameter;
import io.immutables.ecs.Type;
import io.immutables.grammar.Symbol;
import io.immutables.grammar.TreeProduction;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okio.Okio;
import org.immutables.value.Value.Immutable;
import static java.util.Objects.requireNonNullElse;

class Compiler {
	final List<Src> sources = new ArrayList<>();
	final List<String> problems = new ArrayList<>();
	final ListMultimap<String, PerSource> moduleSources = ArrayListMultimap.create();
	final Map<String, Definition.Module> definedModules = new HashMap<>();

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
		modulesForImports.put(systemModule.name(), systemModule);

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
				buildModule(source, source.unit, locallyKnownTypes, moduleBuilder);
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
							.map(
									m -> mergeInlineParameters(problems, m, definedModules)));
			definedModules.put(moduleName, module);
		}

		ok = problems.isEmpty();
		return ok;
	}

	private static ImportModule toModuleForImports(Definition.Module module) {
		return new ImportModule.Builder()
				.name(module.name())
				.addAllTypes(module.definitions().only(Definition.OfType.class))
				.build();
	}

	private Vect<String> compilationOrder() {
		assert !moduleSources.isEmpty();
		// cycles are not properly detected
		var order = new ArrayDeque<String>();
		visitOrderModules(order, moduleSources.keySet());
		return Vect.from(order);
	}

	private void visitOrderModules(Deque<String> order, Iterable<String> toVisit) {
		for (String module : toVisit) {
			if (!moduleSources.containsKey(module)) {
				// could be system or some special external or predef
				continue;
			}
			if (!order.contains(module)) {
				order.addFirst(module);
				for (var source : moduleSources.get(module)) {
					visitOrderModules(order, source.importedModules);
				}
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
				problems.add(filename + ":" + " Cannot read source file\n" + ex);
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

		class Builder extends ImmutableImportModule.Builder {}
	}

	static Definition.OfType builtin(String name) {
		return new Definition.OfType() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public Vect<Type.Feature> features() {
				return Vect.of();
			}

			@Override
			public Vect<Constraint> constraints() {
				return Vect.of();
			}

			@Override
			public Vect<Type.Variable> parameters() {
				return Vect.of();
			}

			@Override
			public String toString() {
				return "builtin(" + name + ")";
			}
		};
	}

	@Immutable
	interface LocallyKnownTypes {
		Map<String, Type.Reference> types();

		Map<Type.Reference, Definition.OfType> imported();

		class Builder extends ImmutableLocallyKnownTypes.Builder {}
	}

	private static void printModule(Definition.Module module) throws IOException {
		Resolver resolver = Codecs.builtin().toResolver();
		JsonWriter writer = JsonWriter.of(
				Okio.buffer(
						Okio.sink(System.err)));
		writer.setIndent("  ");
		Codec.Out out = OkJson.out(writer);
		resolver.get(Definition.Module.class).encode(out, module);
		writer.close();
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
				moduleNameSlot.setPlain(m.name().toString());
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
				imported.add(i.name().toString());
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
		var importedNameSet = new HashSet<String>();

		var locallyBuilder = new LocallyKnownTypes.Builder();

		new SyntaxTrees.Visitor() {
			@Override
			public void caseImportDeclaration(SyntaxTrees.ImportDeclaration i) {
				var maybeModule = importResolver.getModule(i.name().toString());

				if (maybeModule.isPresent()) {
					var m = maybeModule.get();

					for (var t : m.types()) {
						if (localNameSet.contains(t.name())) {
							reporter.problem(i,
									"Imported type from `" + m.name() + "` conflicts with already defined type: " + t.name(), "");
						} else if (importedNameSet.contains(t.name())) {
							reporter.problem(i,
									"Imported type `" + m.name() + "` conflicts with another import: " + t.name(), "");
						} else {
							importedNameSet.add(t.name());
							var ref = Type.Reference.of(m.name(), t.name());
							locallyBuilder.putTypes(t.name(), ref);
							locallyBuilder.putImported(ref, t);
						}
					}
				} else reporter.problem(i, "Cannot find module " + i.name(),
						"It is not declared or spelled differently");
			}

			@Override
			public void caseTypeDeclaration(SyntaxTrees.TypeDeclaration t) {
				var name = t.name().toString();
				if (importedNameSet.contains(name)) {
					reporter.problem(t, "Local type " + t.name() + " have a name conflicting with imported type",
							"Type of the same name was already declared in compilation unit");
				} else if (!localNameSet.add(name)) {
					reporter.problem(t, "Cannot redeclare type " + t.name(),
							"Type of the same name was already declared in compilation unit");
				} else {
					locallyBuilder.putTypes(name, Type.Reference.of(moduleName, name));
				}
			}
		}.caseUnit(unit);

		return locallyBuilder.build();
	}

	static void buildModule(
			Reporter reporter,
			SyntaxTrees.Unit unit,
			LocallyKnownTypes localTypeNames,
			Definition.Module.Builder moduleBuilder) {

		var moduleScope = new ModuleScope(reporter, localTypeNames);

		new SyntaxTrees.Visitor() {
			@Override
			public void caseTypeDeclaration(SyntaxTrees.TypeDeclaration typeDecl) {
				@Nullable var topConstructor = typeDecl.constructor().orElse(null);

				if (topConstructor instanceof SyntaxTrees.ConstructorCases) {
					var constructorCases = (SyntaxTrees.ConstructorCases) topConstructor;

					var ct = new Definition.CaseTypeDefinition.Builder();
					var name = typeDecl.name().toString();
					ct.name(name);

					var signatureScope = new SignatureScope(moduleScope);
					for (Symbol s : typeDecl.typeParameter()) {
						@Nullable var v = signatureScope.introduceParameter(s.toString());
						if (v != null) ct.addParameters(v);
						else reporter.problem(typeDecl, "Duplicate type parameter: " + s, "");
					}

					for (var constructorCase : constructorCases.cases()) {
						Optional<SyntaxTrees.Parameter> parameter = constructorCase.constructor();
						var constructor = buildConstructor(reporter, signatureScope, parameter);
						ct.putConstructors(constructorCase.name().toString(), constructor);
					}

					if (reporter.ok()) moduleBuilder.addDefinitions(ct.build());
				} else if (topConstructor instanceof SyntaxTrees.ConstructorParameter || topConstructor == null) {
					var t = new Definition.DataTypeDefinition.Builder();
					var name = typeDecl.name().toString();
					t.name(name);

					var signatureScope = new SignatureScope(moduleScope);
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

					t.constructor(constructor);

					if (reporter.ok()) moduleBuilder.addDefinitions(t.build());
				}
			}
		}.caseUnit(unit);
	}

	static Definition.Constructor buildConstructor(
			Reporter reporter,
			SignatureScope scope,
			Optional<SyntaxTrees.Parameter> parameter) {

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
				for (; parameterCounter >= 0 /* won't overflow */; parameterCounter++) {
					String n = "arg" + parameterCounter;
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
					var t = f.type();
					for (Symbol s : f.name()) {
						constructorBuilder.addParameters(Definition.NamedParameter.of(
								uniqueName(f, s),
								extractType.match(t, scope)));

						parameterCounter++;
					}
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
			return dt.withConstructor(
					mergeInlineParameters(problems, dt.constructor(), modules));
		}
		if (definition instanceof Definition.CaseTypeDefinition) {
			var ct = (Definition.CaseTypeDefinition) definition;
			return ct.withConstructors(
					Maps.transformValues(ct.constructors(),
							c -> mergeInlineParameters(problems, c, modules)));
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

						datatype: if (definition instanceof Definition.DataTypeDefinition) {
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

						datatype: if (definition instanceof Definition.DataTypeDefinition) {
							var datatype = (Definition.DataTypeDefinition) definition;

							if (!datatype.constructor().takesRecord()) {
								problems.add("Embedding require record constructor, a not positional production "
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

			for (NamedParameter p : constructor.parameters()) {
				parameters.put(p.name(), p);
			}

			return constructor.withMergedParameters(parameters.values());
		}
		return constructor;
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
		final LocallyKnownTypes locally;
		final Reporter reporter;

		ModuleScope(Reporter reporter, LocallyKnownTypes locally) {
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
	}

	static class SignatureScope extends Scope {
		final Scope parent;
		final List<Type.Variable> parameters = new ArrayList<>();

		SignatureScope(Scope parent) {
			this.parent = parent;
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
		protected Type fallback(TreeProduction<SyntaxTrees> v, Scope scope) {
			// TODO in general we need a way to easily get source text from productionIndex / (termBegin,
			// termEnd)
			// right now we cannot get it easily unless Productions/Terms object is passed all along
			return scope.getUnresolved(v.toString() + "%", v);
		}
	};

	private static final ImportModule systemModule = new ImportModule.Builder()
			.name("system")
			.addTypes(builtin("Int"))
			.addTypes(builtin("Long"))
			.addTypes(builtin("String"))
			.addTypes(builtin("Double"))
			.build();

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

	public static void main(String... args) throws IOException {
		var sourceName = "/io/immutables/ecs/fixture/sample.ecs";
		var filename = "sample.ecs";

		Src src = Src.from(Resources.getResource(Compiler.class, sourceName), filename);

		Compiler compiler = new Compiler();
		compiler.add(src);
		if (compiler.compile()) {
			for (var source : compiler.moduleSources.values()) {
				System.out.println(source.productions.show());
			}
			System.out.println("SUCCESS!!");
			for (var m : compiler.definedModules.values()) {
				printModule(m);
			}
		} else {
			for (String p : compiler.problems) {
				System.err.println(p);
			}
			// var terms = SyntaxTerms.from(src.read().toCharArray());
			// var productions = SyntaxProductions.unit(terms);
			// System.err.println(terms.show());
		}
	}
}
