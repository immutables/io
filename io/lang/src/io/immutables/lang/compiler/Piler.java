package io.immutables.lang.compiler;

import com.google.common.io.Resources;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Piler {
	public static void main(String... args) throws IOException {
		String sourceName = "t1.im";
		String content = Resources.toString(
				Resources.getResource(Piler.class, sourceName),
				StandardCharsets.UTF_8);

		if (content.isEmpty() || content.endsWith("\n")) {
			// TEMPORARY FIX for at least two issues of our parser generator:
			// 1. Cannot properly have no content (assertion failed, at EOP)
			// 2. Comment on the last line without newline causes IOOBE
			content += "\n";
		}

		parse(sourceName, content);
	}

	private static void parse(String sourceName, String content) {
		SyntaxTerms terms = SyntaxTerms.from(content.toCharArray());
		SyntaxProductions<SyntaxTrees.Unit> productions = SyntaxProductions.unit(terms);
		System.out.println(productions.show());
		if (productions.ok()) {
			SyntaxTrees.Unit unit = productions.construct();
			System.out.println(unit);
			Piler piler = new Piler();
			piler.process(unit);
		} else {
			System.out.println(productions.messageForFile(sourceName));
			// System.out.println(productions.show());
		}
	}

	void process(SyntaxTrees.Unit unit) {
		new SyntaxTrees.Visitor() {
			@Override
			public void caseTypeDeclaration(SyntaxTrees.TypeDeclaration value) {
				var c = value.constructor();

			}
		}.caseUnit(unit);
	}
}
