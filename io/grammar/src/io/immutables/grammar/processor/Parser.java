package io.immutables.grammar.processor;

import io.immutables.grammar.processor.Grammars.Cardinality;
import io.immutables.grammar.processor.Grammars.MatchMode;
import io.immutables.grammar.processor.GrammarsAst.Alternative;
import io.immutables.grammar.processor.GrammarsAst.AlternativeGroup;
import io.immutables.grammar.processor.GrammarsAst.CharEscapeSpecial;
import io.immutables.grammar.processor.GrammarsAst.CharEscapeUnicode;
import io.immutables.grammar.processor.GrammarsAst.CharLiteral;
import io.immutables.grammar.processor.GrammarsAst.CharRange;
import io.immutables.grammar.processor.GrammarsAst.CharRangeElement;
import io.immutables.grammar.processor.GrammarsAst.CharSeq;
import io.immutables.grammar.processor.GrammarsAst.Comment;
import io.immutables.grammar.processor.GrammarsAst.Group;
import io.immutables.grammar.processor.GrammarsAst.Identifier;
import io.immutables.grammar.processor.GrammarsAst.LexicalKind;
import io.immutables.grammar.processor.GrammarsAst.LexicalTerm;
import io.immutables.grammar.processor.GrammarsAst.Literal;
import io.immutables.grammar.processor.GrammarsAst.LiteralPart;
import io.immutables.grammar.processor.GrammarsAst.ReferencePart;
import io.immutables.grammar.processor.GrammarsAst.SyntaxProduction;
import io.immutables.grammar.processor.GrammarsAst.Unit;
import org.immutables.trees.ast.Extractions;
import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.ExplicitActionsOnly;
import org.parboiled.annotations.Label;
import org.parboiled.annotations.MemoMismatches;
import org.parboiled.annotations.SuppressSubnodes;
import static io.immutables.grammar.processor.ParserActions.tagged;
import static io.immutables.grammar.processor.ParserActions.with;

/**
 * Parboiled parser to parse grammar definitions
 */
@ExplicitActionsOnly
class Parser extends BaseParser<Object> {

	Rule Grammar() {
		return Sequence(Unit.builder(),
				OneOrMore(
				FirstOf(
						LexicalKind(),
						LexicalTerm(),
						Production(),
						Blank())),
				Unit.build(), EOI);
	}

	Rule Production() {
		return Sequence(SyntaxProduction.builder(),
				FirstOf(
						Sequence("(", Identifier(), ")", SyntaxProduction.name(), SyntaxProduction.ephemeral(true)),
						Sequence(Identifier(), SyntaxProduction.name())),
				Newline(),
				FirstOf(
						Sequence(Identation(),
								ProductionSingular(),
								SyntaxProduction.addAlternatives()),
						OneOrMore(Identation(), "|", Spacing(),
								ProductionAlternative(),
								SyntaxProduction.addAlternatives(),
								Newline())),
				SyntaxProduction.build(), Unit.addParts());
	}

	Rule ProductionSingular() {
		return Sequence(Alternative.builder(),
				OneOrMore(ProductionPart(), Alternative.addParts()),
				Alternative.singular(true), Alternative.build());
	}

	Rule ProductionAlternative() {
		return Sequence(Alternative.builder(),
				OneOrMore(ProductionPart(), Alternative.addParts()), Alternative.build());
	}

	Rule ProductionPart() {
		return Sequence(
				Optional(Tag()),
				FirstOf(
						NotPart(),
						AndPart(),
						AlternativeGroupPart(),
						GroupPart(),
						LiteralPart(),
						ReferencePart()),
				Optional(Cadinality()), tagged());
	}

	Rule AndPart() {
		return Sequence("&", Spacing(), ProductionPart(), with(MatchMode.AND));
	}

	Rule NotPart() {
		return Sequence("!", Spacing(), ProductionPart(), with(MatchMode.NOT));
	}

	Rule GroupPart() {
		return Sequence("(", Spacing(), Group.builder(),
				ProductionPart(), Group.addParts(),
				OneOrMore(ProductionPart(), Group.addParts()), ")", Spacing(), Group.build());
	}

	Rule AlternativeGroupPart() {
		return Sequence("(", Spacing(), AlternativeGroup.builder(),
				AlternativeGroupPartAlternative(), AlternativeGroup.addAlternatives(),
				OneOrMore("|", Spacing(), AlternativeGroupPartAlternative(), AlternativeGroup.addAlternatives()),
				")", Spacing(), AlternativeGroup.build());
	}

	Rule AlternativeGroupPartAlternative() {
		return Sequence(Alternative.builder(),
				FirstOf(
						LiteralPart(),
						ReferencePart()), Alternative.addParts(),
				Alternative.build());
	}

	Rule LiteralPart() {
		return Sequence(LiteralPart.builder(),
				LexicalIdentifier(), LiteralPart.literal(), LiteralPart.build());
	}

	Rule ReferencePart() {
		return Sequence(ReferencePart.builder(),
				Identifier(), ReferencePart.reference(),
				Optional("^", Spacing(), ReferencePart.lifted(true)),
				ReferencePart.build());
	}

	Rule LexicalTerm() {
		return Sequence(LexicalTerm.builder(),
				Optional(Identation()), LexicalIdentifier(), LexicalTerm.id(),
				"~", Spacing(),
				OneOrMore(Sequence(LexicalTermPart(), LexicalTerm.addParts())),
				Optional(FirstOf(
						Sequence("&", Spacing(), LexicalTermPart(), LexicalTerm.and()),
						Sequence("!", Spacing(), LexicalTermPart(), LexicalTerm.not()))),
				LexicalTerm.build(), Unit.addParts());
	}

	Rule LexicalKind() {
		return Sequence(
				Identifier(), ":", Spacing(), Optional(Newline()),
				LexicalKind.of(), Unit.addParts());
	}

	Rule LexicalIdentifier() {
		return FirstOf(
				StringLiteral(),
				StringKind());
	}

	Rule LexicalTermPart() {
		return FirstOf(
				CharRange(),
				CharSeq());
	}

	Rule CharSeq() {
		return Sequence(CharSeq.builder(),
				StringLiteral(), CharSeq.literal(), CharSeq.build(), Optional(Cadinality()));
	}

	Rule CharRange() {
		return Sequence(
				"[", CharRange.builder(),
				OneOrMore(CharRangeElement(), CharRange.addElements()),
				"]", CharRange.build(),
				Optional(Cadinality()),
				Spacing());
	}

	Rule CharRangeElement() {
		return Sequence(
				CharRangeElement.builder(),
				Optional("^", CharRangeElement.negated(true)),
				CharDef(), CharRangeElement.from(),
				Optional("-", CharDef(), CharRangeElement.to()),
				CharRangeElement.build());
	}

	Rule CharDef() {
		return FirstOf(
				Sequence("\\",
						FirstOf(
								EscapeSpecial(),
								UnicodeEscape(),
								EscapedChar())),
				LiteralChar());
	}

	@Label("EscapeSpecial\\[nrcfbs-0]")
	Rule EscapeSpecial() {
		return Sequence(AnyOf("nrtfbs[]^-\\0"), CharEscapeSpecial.of());
	}

	@Label("EscapedChar\\{ascii !letters !digits !whitespace}")
	Rule EscapedChar() {
		return Sequence(TestNot(LiteralChar()), CharRange((char) 0x20, (char) 0x7f), CharLiteral.of());
	}

	@Label("UnicodeEscape\\uXXXX")
	Rule UnicodeEscape() {
		return Sequence("u", OneOrMore(CharRange('0', '9')), CharEscapeUnicode.of());
	}

	@Label("[a-zA-Z0-9]")
	Rule LiteralChar() {
		return Sequence(
				FirstOf(
						CharRange('A', 'Z'),
						CharRange('a', 'z'),
						CharRange('0', '9'),
						AnyOf(Codepoint.RANGE_UNSAFE.removeFrom(Codepoint.SPECIAL_CHARS))), CharLiteral.of());
	}

	Rule Cadinality() {
		return FirstOf(
				Sequence("?", with(Cardinality.C0_1), Spacing()),
				Sequence("+", with(Cardinality.C1_N), Spacing()),
				Sequence("*", with(Cardinality.C0_N), Spacing()));
	}

	Rule Blank() {
		return Sequence(Spacing(), Optional(LineComment()), Newline());
	}

	Rule LineComment() {
		return Sequence("--", ZeroOrMore(NoneOf("\n\r"), Comment.of(), Unit.addParts()));
	}

	@SuppressSubnodes
	Rule Newline() {
		return FirstOf("\r\n", "\n\r", "\n");
	}

	@SuppressSubnodes
	@MemoMismatches
	Rule Identifier() {
		return Sequence(
				IdentifierChars(), Identifier.of(),
				Spacing());
	}

	@SuppressSubnodes
	@MemoMismatches
	Rule Tag() {
		return Sequence(
				IdentifierChars(), Identifier.of(), ":",
				Spacing());
	}

	Rule IdentifierChars() {
		return Sequence(
				CharRange('a', 'z'),
				ZeroOrMore(FirstOf(
						CharRange('a', 'z'),
						CharRange('0', '9'),
						'-')));
	}

	@SuppressSubnodes
	@MemoMismatches
	Rule StringLiteral() {
		return Sequence("'", OneOrMore(NoneOf("'")), Literal.of(), "'", Spacing());
	}

	@SuppressSubnodes
	@MemoMismatches
	Rule StringKind() {
		return Sequence("<",
				Literal.builder(),
				Literal.placeholder(true),
				IdentifierChars(), Literal.value(Extractions.matched()),
				Literal.build(),
				">", Spacing());
	}

	@SuppressSubnodes
	Rule Spacing() {
		return ZeroOrMore(AnyOf(" \t"));
	}

	@SuppressSubnodes
	Rule Identation() {
		return OneOrMore(AnyOf(" \t"));
	}
}
