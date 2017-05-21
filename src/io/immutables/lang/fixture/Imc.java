package io.immutables.lang.fixture;

import com.google.common.io.Resources;
import io.immutables.lang.SyntaxTrees.Unit;
import io.immutables.lang.UnitCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Imc {
	public static void main(String... args) throws Exception {
		UnitCompiler compiler = parserFor("debug.im");
		if (compiler.compile()) {
			Unit unit = compiler.unit();
			System.out.println(unit);
		} else {
			System.out.println(compiler.message());
			System.out.println();
			Unit unit = compiler.unit();
			if (unit != null) {
				System.out.println(unit);
			}
		}
	}

	private static UnitCompiler parserFor(String sourceName) throws IOException {
		return new UnitCompiler(Imc.class.getPackage().getName(),
				sourceName,
				Resources.toString(
						Resources.getResource(Imc.class, sourceName),
						StandardCharsets.UTF_8));
	}
}
