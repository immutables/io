package io.immutables.lang.fixture;

import com.google.common.io.Resources;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees.Unit;
import java.nio.charset.StandardCharsets;

public class Imc {
	public static void main(String... args) throws Exception {
		String sourceName = "/io/immutables/lang/fixture/debug.im";

		String content = Resources.toString(
				Resources.getResource(Imc.class, sourceName),
				StandardCharsets.UTF_8);

		SyntaxTerms terms = SyntaxTerms.from(content.toCharArray());
		SyntaxProductions<Unit> productions = SyntaxProductions.unit(terms);
		System.out.println(productions.show());
		if (productions.ok()) {
			System.out.println(productions.construct());
		} else {
			System.out.println(productions.messageForFile(sourceName));
			//System.out.println(productions.show());
		}
	}
}
