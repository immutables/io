package io.immutables.build.dot;

import com.google.common.io.Resources;
import io.immutables.build.dot.DotTrees.Digraph;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestDot {
	@Test
	public void parse() throws IOException {
		char[] source = Resources.asCharSource(
				getClass().getResource("TestDot.dot"), StandardCharsets.UTF_8).read().toCharArray();

		DotProductions<Digraph> productions = DotProductions.digraph(DotTerms.from(source));
		that().is(productions.ok());

		Digraph digraph = productions.construct();
		that(digraph.id()).hasToString("\"graph1\"");
		that(digraph.edge()).hasSize(3);
	}
}
