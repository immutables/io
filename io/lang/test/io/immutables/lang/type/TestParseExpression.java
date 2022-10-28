package io.immutables.lang.type;

import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import org.junit.Test;
import static io.immutables.lang.SyntaxTrees.*;

public class TestParseExpression {

	@Test
	public void parseCheck() {
		Expression expression = expression("a().b(1, 2 + c(d))");
		System.out.println(expression);
		Expression expression1 = expression("a{b: 1, c: 2 + d}");
		System.out.println(expression1);
		Expression expression2 = expression("a.b(1, 2 + c)");
		System.out.println(expression2);
		Expression expression3 = expression("a.b(1, \"c\")");
		System.out.println(expression3);
		Expression expression4 = expression("a.b.c.d(1).e(\"f\").g");
		System.out.println(expression4);
	}

	private static Expression expression(String code) {
		SyntaxTerms terms = SyntaxTerms.from(code.toCharArray());
		SyntaxProductions<Expression> productions = SyntaxProductions.expression(terms);
		System.out.println(productions.show());
		if (productions.ok()) {
			return productions.construct();
		}
		System.err.println(productions.message());
		throw new AssertionError(productions.messageForFile("<expression>"));
	}
}
