package io.immutables.lang.type;

import io.immutables.Nullable;
import io.immutables.collect.Vect;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

final class Features {
	private Features() {}

	static final class Registry implements Feature.Resolver {
		private final Map<Type.Tystructor, PerTystructor> featuresPerTy = new HashMap<>();

		@Override public @Nullable Feature get(Type on, String name) {
			if (on instanceof Type.Tystructor) {
				var tystructor = (Type.Tystructor) on;
				var perTy = featuresPerTy.get(tystructor);
				if (perTy != null) {
					return perTy.get(name, Vect.of());
				}
			} else if (on instanceof Type.Nominal) {
				var nominal = (Type.Nominal) on;
				var perTy = featuresPerTy.get(nominal.tystructor());
				if (perTy != null) {
					return perTy.get(name, nominal.arguments());
				}
			}
			return null;
		}

		Registry put(Type.Tystructor tystructor, String name, Vect<Type.Variable> parameters, Type in, Type out) {
			featuresPerTy.computeIfAbsent(tystructor, PerTystructor::new)
					.put(name, in, out, parameters);
			return this;
		}
	}

	private static final class FeatureInfo {
		Type in;
		Type out;
		Vect<Type.Variable> parameters;
	}

	private static final class ParameterPosition {
		Type.Tystructor on;
		int position;
	}

	private static final class PerTystructor {
		private final Type.Tystructor tystructor;
		private final Map<String, FeatureInfo> features = new HashMap<>();
		private final Vect<Type.Variable> tyParameters;

		PerTystructor(Type.Tystructor tystructor) {
			this.tystructor = tystructor;
			this.tyParameters = tystructor.parameters();
		}

		void put(String name, Type in, Type out, Vect<Type.Variable> parameters) {
			var f = new FeatureInfo();
			f.in = in;
			f.out = out;
			f.parameters = parameters;

			features.put(name, f);
		}

		@Nullable Feature get(String name, Vect<Type> argumentsOfType) {
			var feature = features.get(name);
			if (feature != null) {
				if (tyParameters.isEmpty() && feature.parameters.isEmpty()) {
					return createFeature(name, feature.in, feature.out, Vect.of());
				}

				//var variables = Vect.builder();

				// For the type checking we create fresh feature type variables for each
				// method invocation
				var freshVariables = feature.parameters.map(v -> Types.allocate(v.name()));

				var substitutions = new IdentityHashMap<Type.Variable, Type>(
						tyParameters.size() + feature.parameters.size());

				for (var i = 0; i < tyParameters.size(); i++) {
					substitutions.put(tyParameters.get(i), argumentsOfType.get(i));
				}

				for (var i = 0; i < feature.parameters.size(); i++) {
					var existing = substitutions.put(feature.parameters.get(i), freshVariables.get(i));
					assert existing != null;
				}

				var transformer = new Visitor.Transform<Void>() {
					@Override public Type variable(Type.Variable v, Void in) {
						return substitutions.getOrDefault(v, v);
					}
				};
				var inType = feature.in.accept(transformer, null);
				var outType = feature.out.accept(transformer, null);

				return createFeature(name, inType, outType, freshVariables);
			}
			return null;
		}

		public Feature createFeature(String name, Type in, Type out, Vect<Type.Variable> variables) {
			return new Feature() {
				@Override public Type in() {
					return in;
				}

				@Override public Type out() {
					return out;
				}

				@Override public Vect<Type.Variable> variables() {
					return variables;
				}

				@Override public String name() {
					return name;
				}

				@Override public String toString() {
					return "." + name
							+ (in instanceof Type.Product ? in : ("(" + in + ")"))
							+ " -> " + out
							+ (variables.isEmpty() ? "" : (" :: " + variables.join(", ")));
				}
			};
		}
	}
}
