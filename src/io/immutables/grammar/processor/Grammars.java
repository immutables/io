package io.immutables.grammar.processor;

import io.immutables.collect.Vect;
import io.immutables.grammar.Escapes;
import java.util.Optional;
import org.immutables.trees.Trees.Ast;
import org.immutables.trees.Trees.Transform;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Ast
@Transform
@Enclosing
interface Grammars {

	interface Tagged {
		Tagged withTag(Identifier tag);
		Optional<Identifier> tag();
	}

	interface Matched {
		Matched withMode(MatchMode mode);

		default @Default MatchMode mode() {
			return MatchMode.CONSUME;
		}
	}

	interface Cardinal {
		Cardinal withCardinality(Cardinality cardinality);

		default @Default Cardinality cardinality() {
			return Cardinality.C1_1;
		}
	}

	interface ProductionPart extends Tagged, Cardinal, Matched {}

	interface TermPart extends Cardinal {}

	@Immutable
	interface CharSeq extends TermPart {
		Literal literal();

		class Builder extends ImmutableGrammars.CharSeq.Builder {}
	}

	@Immutable
	interface CharRange extends TermPart {
		Vect<CharRangeElement> elements();

		class Builder extends ImmutableGrammars.CharRange.Builder {}
	}

	@Immutable
	interface CharRangeElement {
		Char from();
		Optional<Char> to();

		default @Default boolean negated() {
			return false;
		}

		class Builder extends ImmutableGrammars.CharRangeElement.Builder {}
	}

	interface Char {}

	@Immutable
	interface CharLiteral extends Char {
		@Parameter
		String value();
	}

	@Immutable
	interface CharEscapeSpecial extends Char {
		@Parameter
		String value();
	}

	@Immutable
	interface CharEscapeUnicode extends Char {
		@Parameter
		String value();
	}

	@Immutable
	interface LiteralPart extends ProductionPart {
		Literal literal();

		class Builder extends ImmutableGrammars.LiteralPart.Builder {}
	}

	@Immutable
	interface ReferencePart extends ProductionPart {
		Identifier reference();

		class Builder extends ImmutableGrammars.ReferencePart.Builder {}
	}

	interface Production {
		Vect<ProductionPart> parts();
	}

	@Immutable
	interface Group extends Production, ProductionPart {
		class Builder extends ImmutableGrammars.Group.Builder {}
	}

	@Immutable
	interface AlternativeGroup extends ProductionPart {
		Vect<Alternative> alternatives();
		class Builder extends ImmutableGrammars.AlternativeGroup.Builder {}
	}

	@Immutable
	interface Alternative extends Production {
		default @Default boolean singular() {
			return false;
		}
		static Alternative of(Iterable<? extends ProductionPart> parts) {
			return new Builder().addAllParts(parts).build();
		}
		class Builder extends ImmutableGrammars.Alternative.Builder {}
	}

	interface UnitPart {}

	@Immutable
	interface Comment extends UnitPart {
		@Parameter
		String value();
	}

	@Immutable
	interface SyntaxProduction extends UnitPart {
		Identifier name();
		Vect<Alternative> alternatives();
		default @Default boolean ephemeral() {
			return false;
		}
		class Builder extends ImmutableGrammars.SyntaxProduction.Builder {}
	}

	@Immutable
	interface Upcasting extends UnitPart {
		Identifier name();
		Vect<Identifier> alternatives();
		class Builder extends ImmutableGrammars.Upcasting.Builder {}
	}

	@Immutable
	interface LexicalTerm extends UnitPart {
		Literal id();
		Vect<TermPart> parts();
		Optional<TermPart> not();
		Optional<TermPart> and();

		class Builder extends ImmutableGrammars.LexicalTerm.Builder {}
	}

	@Immutable
	interface LexicalKind extends UnitPart {
		@Parameter
		Identifier kind();
	}

	@Immutable
	interface Unit {
		Vect<UnitPart> parts();

		class Builder extends ImmutableGrammars.Unit.Builder {}
	}

	@Immutable
	abstract class Identifier {
		@Parameter
		abstract String value();
		@Override
		public String toString() {
			return value();
		}
		public static Identifier of(String value) {
			return ImmutableGrammars.Identifier.of(value);
		}
	}

	@Immutable
	abstract class Literal {
		@Parameter
		abstract String value();
		@Default
		boolean placeholder() {
			return false;
		}
		@Override
		public String toString() {
			return Escapes.singleQuote(value());
		}
		public static Literal of(String key) {
			return ImmutableGrammars.Literal.of(key);
		}
		static class Builder extends ImmutableGrammars.Literal.Builder {}
	}

	enum MatchMode {
		CONSUME,
		NOT,
		AND;

		boolean notConsume() {
			return this != CONSUME;
		}
	}

	enum Cardinality {
		C1_1(""),
		C0_1("?"),
		C0_N("*"),
		C1_N("+");

		private final String mark;

		Cardinality(String mark) {
			this.mark = mark;
		}

		boolean isMaybeOne() {
			return this == C0_1;
		}

		boolean isExactlyOne() {
			return this == C1_1;
		}

		boolean isAtLeastOne() {
			return this == C1_1 || this == C1_N;
		}

		boolean isAtMostOne() {
			return this == C0_1 || this == C1_1;
		}

		boolean isMultiple() {
			return this == C0_N || this == C1_N;
		}

		boolean isZeroed() {
			return this == C0_1 || this == C0_N;
		}

		@Override
		public String toString() {
			return mark;
		}

		Cardinality span(Cardinality c) {
			boolean isMultiple = isMultiple() || c.isMultiple();
			boolean isZeroed = isZeroed() || c.isZeroed();
			if (isMultiple && isZeroed) {
				return C0_N;
			}
			if (isMultiple) {
				return C1_N;
			}
			if (isZeroed) {
				return C0_1;
			}
			return C1_1;
		}

		Cardinality append(Cardinality c) {
			return isAtLeastOne() || c.isAtLeastOne()
					? C1_N
					: C0_N;
		}
	}
}
