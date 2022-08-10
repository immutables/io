package io.immutables.lang.type;

import io.immutables.collect.Vect;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class Typecheck {
	private final Scope scope;
	private Features.Registry featureRegistry;

	final Map<Production, Resolution> rs = new IdentityHashMap<>();
	final Map<Production, Type> types = new IdentityHashMap<>();
	final Set<Type.Variable> resolvables = Collections.newSetFromMap(new IdentityHashMap<>());

	Typecheck(Scope scope, Features.Registry registry) {
		this.scope = scope;
		this.featureRegistry = registry;
	}

	void perform(Production production) {
		new CheckTrav().caseProduction(production);

		var t = types.get(production);
		//		if (t == null) throw new AssertionError("Untyped " + production);

		new ShowTrav().caseProduction(production);
	}

	class CheckTrav extends Production.Traversal {
		@Override
		protected void caseProduct(Production.Product product) {
			super.caseProduct(product);

			for (var i = 0; i < product.components.size(); i++) {
				var component = product.components.get(i);

				var t = types.get(component);

				//			if (t == null) throw new AssertionError("Untyped " + component);
			}
		}

		@Override protected void caseNumberLiteral(Production.NumberLiteral number) {
			types.put(number, Play.i32);
		}

		@Override protected void caseAccess(Production.Access access) {
			var refType = scope.typeFor(access.name);
			types.put(access, refType);
		}

		@Override protected void caseFeatureApply(Production.FeatureApply apply) {
			var expectedType = types.get(apply);

			// in this language we cannot impose any type on receiver
			// for the purpose of the type resolution
			featureOn(apply.on);
			Type receiverType = types.get(apply.on);
			assert receiverType != null : "receiver not typed";

			var feature = featureRegistry.get(receiverType, apply.name);

			if (feature == null) {
				System.err.println("Missing feature " + apply.name + " on " + apply.on + ": " + receiverType);
				return;
			}

			System.out.println("F| " + feature);

			var variables = feature.variables();

			variables.forEach(resolvables::add);


			// do type in
			featureIn(apply.in);
			Type inputType = types.get(apply.in);
			assert inputType != null : "receiver not typed";

			match(feature.in(), inputType);


			// featureName(apply.name);
			// featureOn(apply.on);


			if (!variables.isEmpty()) {
				// System.out.println("11111");
				types.put(apply, feature.out());
			} else {
				// System.out.println("22222");
				types.put(apply, feature.out());
			}
		}
	}

	Type match(Type actual, Type expected) {
		return expected;
	}

	abstract static class Resolution implements Type {
		@Override public <I, O> O accept(Visitor<I, O> v, I in) {
			return v.otherwise(this, in);
		}

		class Capture extends Resolution {}

		class Assuming extends Resolution {}

		class Mismatch extends Resolution {}
	}

	private class ShowTrav extends Production.Traversal {
		@Override protected void caseExpression(Production.Expression expression) {
			super.caseExpression(expression);
			System.out.println("T| " + expression + "  \t\t\t\t ==>  " + types.get(expression));
		}
	}
}
