package io.immutables.lang.node;

import java.util.*;

class FeatureRegistry implements Scope {
	private final Map<String, Typed> locals = new HashMap<>();
	private final Map<Tyctor, PerTyctor> perType = new IdentityHashMap<>();

	@Override public FeatureArrow feature(Typed on, String name) {
		var f = new FeatureArrow();
		f.name = name;

		var perType = this.perType.get(on.tyctor);
		if (perType != null) {
			var data = perType.features.get(name);
			if (data != null) {
				data.prepare(f);
				return f;
			}
		}

		f.missing = true;
		return f;
	}

	@Override public FeatureArrow feature(String name) {
		var f = new FeatureArrow();
		f.name = name;

		var t = locals.get(name);
		if (t != null) {
			f.out = t;
			f.in = BuiltinTypes.Empty;
			return f;
		}

		f.missing = true;
		return f;
	}

	void putAccess(String name, Typed out) {
		locals.put(name, out);
	}

	FeatureBuilder putFeature(Tyctor tyctor, String name) {
		var t = perType.computeIfAbsent(tyctor, PerTyctor::new);
		var b = new FeatureBuilder();
		t.features.put(name, b.data);
		return b;
	}

	class FeatureBuilder {
		private final FeatureData data = new FeatureData();

		Typed parameter(String name) {
			var p = new Typed();
			p.kind = Typed.Kind.Parameter;
			p.name = name;

			data.parameters.add(p);
			return p;
		}

		FeatureBuilder in(Typed in) {
			data.in = in;
			return this;
		}

		FeatureBuilder out(Typed out) {
			data.out = out;
			return this;
		}

		void done() {}
	}

	private final class PerTyctor {
		final Tyctor type;
		final Map<String, FeatureData> features = new HashMap<>();

		PerTyctor(Tyctor type) {
			this.type = type;
		}
	}

	private class FeatureData {
		final List<Typed> parameters = new ArrayList<>();
		Typed in;
		Typed out;

		void prepare(FeatureArrow arrow) {
			allocateFreshVariables(arrow);

			arrow.in = process(arrow, in);
			arrow.out = process(arrow, out);
		}

		private Typed process(FeatureArrow arrow, Typed type) {
			if (type == null) return BuiltinTypes.Empty;
			return type.cloneOrUnique(t -> replaceParametersWithVariables(t, arrow.variables));
		}

		private void allocateFreshVariables(FeatureArrow arrow) {
			arrow.variables = new Typed[parameters.size()];

			for (var i = 0; i < arrow.variables.length; i++) {
				var p = parameters.get(i);

				var v = new Typed();
				v.kind = Typed.Kind.Variable;
				v.name = p.name;
				//v.constraints TODO
				arrow.variables[i] = v;
			}
		}

		void replaceParametersWithVariables(Typed t, Typed[] variables) {
			if (t.kind == Typed.Kind.Parameter) {
				for (var i = 0; i < parameters.size(); i++) {

				}
			}
		}
	}
}
