package io.immutables.grammar.fixture;

import com.google.common.base.Strings;
import io.immutables.grammar.Source;
import io.immutables.grammar.fixture.ExprTrees.Expression;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestFixture {
	@Test
	public void someLex() {
		that(lex("ififif if = == ="))
				.is("'iden'ififif 'space'\\s 'if'if 'space'\\s 'let'= 'space'\\s 'eq'== 'space'\\s 'let'=");

		// testing not guard
		that(lex("()(())"))
				.is("'lp'( 'rp') ?( 'lp'( 'rp') 'rp')");

		that(lex("affiliate affect if (affiliate)"))
				.is("'af'affiliate 'space'\\s 'ff'affect 'space'\\s 'if'if 'space'\\s 'lp'( 'af'affiliate 'rp')");

		// testing and guard
		that(lex("<<<")).is("'<_<'< '<_<'< '<'<");
	}

	private String lex(String input) {
		SomeLexLexer lexer = new SomeLexLexer(input.toCharArray());
		lexer.tokenize();
		return lexer.show();
	}

	@Test
	public void exprParse() {
		String string = "1  +[2 ,3,vvgg ] ";

		ExprLexer lexer = new ExprLexer(string.toCharArray());
		lexer.tokenize();
		ExprParser parser = new ExprParser(lexer);
		Expression expr = parser.expression();
		that(expr).hasToString("Expression{left=Constant{}, operator=Operator{}, right=List{elem=[Constant{}, Constant{}, Variable{}]}}");
	}

	@Test
	public void unexpected() {
		char[] input = "1  +[2 ___!,3,vvgg ] ".toCharArray();
		ExprLexer lexer = new ExprLexer(input);

		that().not(lexer.tokenize());
		that().is(lexer.hasUnexpected());
		Source.Range range = lexer.getFirstUnrecognized();
		that(range.begin()).equalTo(Source.Position.of(7, 1, 8));
		that(range.end()).equalTo(Source.Position.of(8, 1, 9));
	}

	@Test
	public void prematureEof() {
		char[] input = "1\0\0".toCharArray();
		ExprLexer lexer = new ExprLexer(input);

		that().not(lexer.tokenize());
		that().is(lexer.hasPrematureEof());
	}

	@Test
	public void parsingError() {
		String string = "1  +[2 ,3,abc def ] ";
		ExprLexer lexer = new ExprLexer(string.toCharArray());
		that().is(lexer.tokenize());
		ExprParser parser = new ExprParser(lexer);
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
		ExprLexer lexer = new ExprLexer(input.toCharArray());
		lexer.tokenize();
		Source.Range range = lexer.getFirstUnrecognized();
		System.out.println(range);
		Source.Excerpt excerpt = new Source.Excerpt(lexer.getSource());
		System.out.println(excerpt.get(range));
	}
}
