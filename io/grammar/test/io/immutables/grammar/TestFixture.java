package io.immutables.grammar;

import io.immutables.Source;
import io.immutables.grammar.fixture.ExprProductions;
import io.immutables.grammar.fixture.ExprTerms;
import io.immutables.grammar.fixture.ExprTrees.Expression;
import io.immutables.grammar.fixture.SomeLexTerms;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestFixture {
	@Test
	public void someterms() {
		that(terms("ififif if = == ="))
				.is("'iden'ififif 'space'\\s 'if'if 'space'\\s 'let'= 'space'\\s 'eq'== 'space'\\s 'let'=");

		// testing not guard
		that(terms("()(())"))
				.is("'lp'( 'rp') ?( 'lp'( 'rp') 'rp')");

		that(terms("affiliate affect if (affiliate)"))
				.is("'af'affiliate 'space'\\s 'ff'affect 'space'\\s 'if'if 'space'\\s 'lp'( 'af'affiliate 'rp')");

		// testing and guard
		that(terms("<<<")).is("'<_<'< '<_<'< '<'<");
	}

	private String terms(String input) {
		return SomeLexTerms.from(input.toCharArray()).show();
	}

	@Test
	public void exprParseOld() {
		String string = "1  +[2 ,3,vvgg ] ";

		ExprTerms terms = ExprTerms.from(string.toCharArray());
		Expression expr = ExprProductions.expression(terms).construct();
		that(expr).hasToString(
				"Expression{left=Constant{value=1}, operator=Operator{},"
						+ " right=List{elem=[Constant{value=2}, Constant{value=3}, Variable{name=vvgg}]}}");
	}

	@Test
	public void exprParse() {
		String string = "1  +[2 ,3,vvgg ] ";

		ExprTerms terms = ExprTerms.from(string.toCharArray());
		ExprProductions<Expression> productions = ExprProductions.expression(terms);
		Expression expr = productions.construct();
		that(expr).hasToString(
				"Expression{left=Constant{value=1}, operator=Operator{},"
						+ " right=List{elem=[Constant{value=2}, Constant{value=3}, Variable{name=vvgg}]}}");
	}

	@Test
	public void unexpected() {
		char[] input = "1  +[2 ___!,3,vvgg ] ".toCharArray();
		ExprTerms terms = ExprTerms.from(input);

		that().not(terms.ok());
		that().is(terms.hasUnexpected());
		Source.Range range = terms.firstUnexpectedRange();
		that(range.begin).equalTo(Source.Position.of(7, 1, 8));
		that(range.end).equalTo(Source.Position.of(8, 1, 9));
	}

	@Test
	public void unexpectedLeftover() {
		char[] input = "1\0\0".toCharArray();
		ExprTerms terms = ExprTerms.from(input);

		that().not(terms.ok());
		that().is(terms.hasUnexpected());
	}

	@Test
	public void parsingError() {
		String string = "1  +[2 ,3,abc def ] ";
		ExprTerms terms = ExprTerms.from(string.toCharArray());
		that().is(terms.ok());
		ExprProductions<Expression> productions = ExprProductions.expression(terms);
		that().not(productions.ok());
		that().is(productions.hasUnmatched());
		Source.Range range = productions.getUnmatched();
		that(range.begin).equalTo(Source.Position.of(14, 1, 15));
		that(range.end).equalTo(Source.Position.of(17, 1, 18));
	}
}
