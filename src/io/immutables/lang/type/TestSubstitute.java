package io.immutables.lang.type;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestSubstitute {
	final Map<Name, Type> types = new HashMap<>();
	final Type.Resolver resolver = n -> types.computeIfAbsent(n, k -> Type.Unresolved.of(k));

	@Test
	public void emptyNoSubstitution() {
		that(Substitute.init().size()).is(0);
		that(Substitute.init().apply(Type.Empty)).same(Type.Empty);
	}

	@Test
	public void substitute() {
		Type.Variable A = Type.Variable.allocate(Name.of("A"));
		Type.Variable B = Type.Variable.allocate(Name.of("B"));

		Substitute<Type> substitute = Substitute.init()
				.with(A, Type.Undefined)
				.with(B, Type.Empty);

		that(substitute.apply(A)).same(Type.Undefined);
		that(substitute.apply(B)).same(Type.Empty);
		that(substitute.has(A)).is(true);
		that(substitute.has(B)).is(true);
		that(substitute.has(Type.Undefined)).is(false);
	}

	/**
	 * the following checks immutability and functional independence
	 * (regardless of implementation data sharing) of created copy
	 * with additional mapping.
	 */
	@Test
	public void substituteCopyIndependence() {
		Type.Variable A = Type.Variable.allocate(Name.of("A"));

		Substitute<Type> substitute = Substitute.init()
				.with(A, Type.Undefined);

		Substitute<Type> substituteCopyWith = substitute
				.with(Type.Empty, Type.Undefined);

		that(substitute.has(A)).is(true);
		that(substitute.has(Type.Empty)).is(false);

		that(substituteCopyWith.has(A)).is(true);
		that(substituteCopyWith.has(Type.Empty)).is(true);
		that(substituteCopyWith.apply(Type.Empty)).same(Type.Undefined);
	}

	@Test
	public void substituteConstructionLoop() {
		Substitute<Type.Variable> substitute = Substitute.init();

		int count = 100;

		Type.Variable[] parameters = new Type.Variable[count];
		for (int i = 0; i < parameters.length; i++) {
			parameters[i] = Type.Variable.allocate(Name.of("_" + i));
		}

		for (Type.Variable p : parameters) {
			substitute = substitute.with(p, Type.Empty);
		}

		that(substitute.size()).is(count);

		for (Type.Variable p : parameters) {
			that(substitute.apply(p)).equalTo(Type.Empty);
		}
	}
}
