package io.immutables.grammar.fixture;

import io.immutables.grammar.fixture.ExprTrees.Expression;

public class MainFixture {
	public static void main(String... args) {
		String string = "1 + [ 2,3,vgg]";

		ExprTerms terms = ExprTerms.from(string.toCharArray());
		System.out.println("terms " + (terms.ok() ? "ok" : "fail") + ":");
		// System.out.println(terms.show());
		ExprProductions<Expression> expression = ExprProductions.expression(terms);
		System.out.println("expression " + (expression.ok() ? "ok" : "fail") + ":");
		// System.out.println(expression.show());
		System.out.println(expression.message());
		System.out.println(expression.construct());
	}
}
