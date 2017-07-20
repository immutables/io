package io.immutables.grammar.fixture;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import io.immutables.grammar.Productions.Traversal;
import io.immutables.grammar.Productions.Traversal.At;
import io.immutables.grammar.Source;
import io.immutables.grammar.fixture.ExprTrees.Expression;
import io.immutables.grammar.fixture.ExprTrees.Expressions;
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

	public static void main1() {
		String input = Strings.repeat("1 + \n", 10000) + " [2 ___!,3,vvgg ] ";

		// String input = "\n[1\n, 2,\n,3, 4\n, 5, 6, 7,\n n,\n__!] \n\n\n";
		// String input = "\n[1__\n] ";
		// String input = "1__] ";
		ExprTerms terms = ExprTerms.from(input.toCharArray());
		terms.ok();
		Source.Range range = terms.firstUnexpectedRange();
		System.out.println(range);
		System.out.println(terms.highlight(range));
	}

	public static void main(String... args) {
		String input = "1 + [2, a, b , 3] + [c, 1]";
		ExprTerms terms = ExprTerms.from(input.toCharArray());
		if (!terms.ok()) {
			Source.Range range = terms.firstUnexpectedRange();
			System.out.println(range);
			System.out.println(terms.highlight(range));
			return;
		}
		ExprProductions<Expressions> expressions = ExprProductions.expressions(terms);
		if (!expressions.ok()) {
			System.out.println(expressions.message());
			return;
		}
		System.out.println(expressions.show());
		Traversal t = expressions.traverse();
		At at;
		int ind = 0;
		while ((at = t.next()) != At.EOP) {
			if (at != At.PRODUCTION_END) {
				System.out.println(Strings.padEnd(Strings.repeat("  ", ind) + "*", 10, ' ') + t.show());
			}
			if (at == At.PRODUCTION_BEGIN) ind++;
			if (at == At.PRODUCTION_END) ind--;
			System.out.println(terms.highlight(t.range()));
		}
		t = expressions.traverse();

		HashMultiset<Object> multiset = HashMultiset.create();
		while ((at = t.next()) != At.EOP) {
			multiset.add(at);
		}

		System.out.println(multiset);
	}
}
