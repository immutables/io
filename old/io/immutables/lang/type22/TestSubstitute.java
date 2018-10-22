package io.immutables.lang.type22;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestSubstitute {
	final Map<Name, Type22> types = new HashMap<>();
	final Type22.Resolver resolver = n -> types.computeIfAbsent(n, k -> Type22.Unresolved.of(k));

	@Test
	public void emptyNoSubstitution() {
		that(Substitute.init().size()).is(0);
		that(Substitute.init().apply(Type22.Empty)).same(Type22.Empty);
	}

	@Test
	public void substitute() {
		Type22.Variable A = Type22.Variable.allocate(Name.of("A"));
		Type22.Variable B = Type22.Variable.allocate(Name.of("B"));

		Substitute<Type22> substitute = Substitute.init()
				.with(A, Type22.Undefined)
				.with(B, Type22.Empty);

		that(substitute.apply(A)).same(Type22.Undefined);
		that(substitute.apply(B)).same(Type22.Empty);
		that(substitute.has(A)).is(true);
		that(substitute.has(B)).is(true);
		that(substitute.has(Type22.Undefined)).is(false);
	}

	/**
	 * the following checks immutability and functional independence
	 * (regardless of implementation data sharing) of created copy
	 * with additional mapping.
	 */
	@Test
	public void substituteCopyIndependence() {
		Type22.Variable A = Type22.Variable.allocate(Name.of("A"));

		Substitute<Type22> substitute = Substitute.init()
				.with(A, Type22.Undefined);

		Substitute<Type22> substituteCopyWith = substitute
				.with(Type22.Empty, Type22.Undefined);

		that(substitute.has(A)).is(true);
		that(substitute.has(Type22.Empty)).is(false);

		that(substituteCopyWith.has(A)).is(true);
		that(substituteCopyWith.has(Type22.Empty)).is(true);
		that(substituteCopyWith.apply(Type22.Empty)).same(Type22.Undefined);
	}

	@Test
	public void substituteConstructionLoop() {
		Substitute<Type22.Variable> substitute = Substitute.init();

		int count = 100;

		Type22.Variable[] parameters = new Type22.Variable[count];
		for (int i = 0; i < parameters.length; i++) {
			parameters[i] = Type22.Variable.allocate(Name.of("_" + i));
		}

		for (Type22.Variable p : parameters) {
			substitute = substitute.with(p, Type22.Empty);
		}

		that(substitute.size()).is(count);

		for (Type22.Variable p : parameters) {
			that(substitute.apply(p)).equalTo(Type22.Empty);
		}
	}
}
