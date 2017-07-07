package io.immutables.lang.type;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class TestType {
	final Map<Name, Type> types = new HashMap<>();
	final Type.Resolver resolver = n -> types.computeIfAbsent(n, k -> Type.Unresolved.of(k));

	@Test
	public void test() {
		// add(Type.Variable.)

	}
}
