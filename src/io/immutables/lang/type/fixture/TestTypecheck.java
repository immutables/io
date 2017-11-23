package io.immutables.lang.type.fixture;

import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees.Expression;
import org.junit.Test;

// a = a.a(1, 2 + b)
// how?
// starting at let binding we determine target type W from the left
// then we determine type of the expression on the right
// go down recursively
//
public class TestTypecheck {
	@Test
	public void typeCheck() {
		Expression expression = expression("a.a(1, 2 + b)");
		System.out.println(expression);
	}

	private static Expression expression(String code) {
		SyntaxTerms terms = SyntaxTerms.from(code.toCharArray());
		SyntaxProductions<Expression> productions = SyntaxProductions.expression(terms);
		System.out.println(productions.show());
		if (productions.ok()) {
			return productions.construct();
		}
		throw new AssertionError(productions.messageForFile("<expression>"));
	}
}
