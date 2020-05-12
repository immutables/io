package io.immutables.ecs.fixture;

import com.google.common.io.Resources;
import io.immutables.ecs.lang.EcsProductions;
import io.immutables.ecs.lang.EcsTerms;
import io.immutables.ecs.lang.EcsTrees.Unit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MainEcs {

	public static void main(String... args) throws IOException {
		String sourceName = "/io/immutables/ecs/fixture/sample.ecs";

		String content = Resources.toString(
				Resources.getResource(MainEcs.class, sourceName),
				StandardCharsets.UTF_8);

		EcsTerms terms = EcsTerms.from(content.toCharArray());
		EcsProductions<Unit> productions = EcsProductions.unit(terms);
		System.out.println(productions.show());
		if (productions.ok()) {
			System.out.println(productions.construct());
		} else {
			System.out.println(productions.messageForFile(sourceName));
			System.out.println(productions.show());
		}
	}
}
