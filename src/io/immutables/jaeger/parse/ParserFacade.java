package io.immutables.jaeger.parse;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import io.immutables.grammar.AstProduction;
import io.immutables.grammar.LexerBase;
import io.immutables.grammar.Source;
import javax.annotation.Nullable;

public class ParserFacade {
	private final String sourceName;
	private final JaegerLexer lexer;
	private final JaegerParser parser;
	private @Nullable JaegerTrees.Unit unit;
	private CharSequence message = "";

	public ParserFacade(String sourceName, String content) {
		this.sourceName = sourceName;
		this.lexer = new JaegerLexer(content.toCharArray());
		this.parser = new JaegerParser(lexer);
	}

	public CharSequence message() {
		return message;
	}

	public @Nullable JaegerTrees.Unit unit() {
		return unit;
	}

	public boolean parse() {
		lexer.tokenize();
		this.unit = parser.unit();
		if (lexer.hasUnexpected() || lexer.hasPrematureEof() || unit == null) {
			if (unit == null && parser.hasMismatchedToken()) {
				this.message = buildMismatchMessage();
			} else if (lexer.hasUnexpected()) {
				this.message = buildUnexpectedMessage();
			} else if (lexer.hasPrematureEof()) {
				this.message = buildPrematureEofMessage();
			}
			return false;
		}
		if (unit != null) {
			int next = lexer.advance();
			if (next != LexerBase.EOF) {
				this.message = buildMismatchMessage();
				return false;
			}
		}
		return true;
	}

	private CharSequence buildPrematureEofMessage() {
		Source.Range range = lexer.getCurrentRange();
		return sourceName + ":" + range.begin()
				+ " Unexpected tokens starting with `" + range.get() + "` "
				+ printExcerpt(range)
				+ "\n\tUnconsumed tokens left which does not conform to any production";
	}

	private CharSequence buildUnexpectedMessage() {
		Source.Range range = lexer.getFirstUnrecognized();
		return sourceName + ":" + range.begin()
				+ " Unexpected characters `" + range.get() + "`"
				+ printExcerpt(range)
				+ "\n\tCharacters are not forming any recognized token";
	}

	private CharSequence buildMismatchMessage() {
		AstProduction.Id production = parser.getFarthestMismatchedProduction();
		Source.Range range = parser.getFarthestMismatchedToken();
		return sourceName + ":" + range.begin()
				+ " Stumbled on `"
				+ range.get() + "`, a " + parser.getMismatchedActualToken()
				+ " term while expecting " + parser.getMismatchedExpectedToken()
				+ " term in "
				+ production + ""
				+ printExcerpt(range)
				+ "\n\tCannot parse construct such as "
				+ production
				+ " because of mismatched term";
	}

	private String printExcerpt(Source.Range range) {
		StringBuilder excerpt = new Source.Excerpt(lexer.getSource()).get(range);
		return Joiner.on("\n\t").join(FluentIterable.of(new String[] {""}).append(
				Splitter.on('\n')
						.omitEmptyStrings()
						.split(excerpt)));
	}
}
