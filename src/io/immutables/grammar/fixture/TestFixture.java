package io.immutables.grammar.fixture;

import com.google.common.base.Strings;
import io.immutables.grammar.Source;
import io.immutables.grammar.fixture.ExprTrees.Expression;
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
	public void exprParse() {
		String string = "1  +[2 ,3,vvgg ] ";

		ExprTerms terms = ExprTerms.from(string.toCharArray());
		ExprParser parser = new ExprParser(terms);
		Expression expr = parser.expression();
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
		that(range.begin()).equalTo(Source.Position.of(7, 1, 8));
		that(range.end()).equalTo(Source.Position.of(8, 1, 9));
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
		ExprParser parser = new ExprParser(terms);
		that(parser.expression()).isNull();
		that().is(parser.hasMismatchedToken());
		that(parser.getFarthestMismatchedProduction()).same(ExprTrees.Variable.ID);
		Source.Range range = parser.getFarthestMismatchedToken();
		that(range.begin()).equalTo(Source.Position.of(14, 1, 15));
		that(range.end()).equalTo(Source.Position.of(17, 1, 18));
	}

	public static void main(String... args) {
		String input = Strings.repeat("1 + \n", 10000) + " [2 ___!,3,vvgg ] ";

		// String input = "\n[1\n, 2,\n,3, 4\n, 5, 6, 7,\n n,\n__!] \n\n\n";
		// String input = "\n[1__\n] ";
		// String input = "1__] ";
		ExprTerms terms = ExprTerms.from(input.toCharArray());
		terms.ok();
		Source.Range range = terms.firstUnexpectedRange();
		System.out.println(range);
		System.out.println(range.highlight());
	}
}
