package io.immutables.ecs.fixture;

import com.google.common.io.Resources;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees;
import io.immutables.lang.SyntaxTrees.Unit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MainEcs {

	public static void main(String... args) throws IOException {
		var sourceName = "/io/immutables/ecs/fixture/sample.ecs";

		var content = Resources.toString(
				Resources.getResource(MainEcs.class, sourceName),
				StandardCharsets.UTF_8);

		var terms = SyntaxTerms.from(content.toCharArray());
		var productions = SyntaxProductions.unit(terms);

		System.out.println(productions.show());
		if (productions.ok()) {
			Unit unit = productions.construct();

			var v = new SyntaxTrees.Visitor() {

			};
			v.caseUnit(unit);
		} else {
			System.out.println(productions.messageForFile(sourceName));
		}
	}
}
