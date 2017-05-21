package io.immutables.grammar.processor;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.immutables.Unreachable;
import io.immutables.collect.Vect;
import io.immutables.grammar.processor.CodepointMatch.Sequence;
import io.immutables.grammar.processor.CodepointMatch.When;
import io.immutables.grammar.processor.Grammars.Cardinality;
import io.immutables.grammar.processor.Grammars.Char;
import io.immutables.grammar.processor.Grammars.CharEscapeSpecial;
import io.immutables.grammar.processor.Grammars.CharEscapeUnicode;
import io.immutables.grammar.processor.Grammars.CharLiteral;
import io.immutables.grammar.processor.Grammars.CharRange;
import io.immutables.grammar.processor.Grammars.CharRangeElement;
import io.immutables.grammar.processor.Grammars.CharSeq;
import io.immutables.grammar.processor.Grammars.Identifier;
import io.immutables.grammar.processor.Grammars.LexicalKind;
import io.immutables.grammar.processor.Grammars.LexicalTerm;
import io.immutables.grammar.processor.Grammars.Literal;
import io.immutables.grammar.processor.Grammars.LiteralPart;
import io.immutables.grammar.processor.Grammars.ProductionPart;
import io.immutables.grammar.processor.Grammars.TermPart;
import io.immutables.grammar.processor.Grammars.Unit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Derived;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import org.immutables.value.Value.Parameter;
import static com.google.common.base.Preconditions.checkState;

/** Parsed structure of term matching. */
@Immutable
@Enclosing
abstract class TermExpansion {
	/** Literals are used as term ids. */
	abstract Literal id();
	/** Consumable match parts. Cannot be empty */
	abstract Vect<MatchPart> parts();
	/** Lookahead '!' (not) matcher. Cannot be used with {@link #and()} */
	abstract Optional<CodepointMatch> not();
	/** Lookahead '&' (and) matcher. Cannot be used with {@link #not()} */
	abstract Optional<CodepointMatch> and();

	@Default
	Identifier kind() {
		return KIND_DEFAULT;
	}

	boolean ignored() {
		return kind().equals(KIND_IGNORED);
	}

	@Check
	void check() {
		checkState(!not().isPresent() || !and().isPresent(), "& and ! guards cannot be both present");
		checkState(!parts().isEmpty(), "term parts should not be empty: " + this);
		checkState(parts().get(0).cardinality().isAtLeastOne(), "first match should not be optional: " + this);
	}

	/**
	 * Name is a base which can be used for code generation as identifier.
	 * Constructed from id literal, may end up very ugly, but suitable for source code.
	 */
	@Derived
	Identifier name() {
		return toSyntheticIdentifier(id());
	}

	/**
	 * First match is significant for dispatching in lexer.
	 */
	CodepointMatch firstMatch() {
		return parts().get(0).match();
	}

	/** If first match is always simple, i.e. eligible chars have ords 0-127. */
	boolean isFirstSimpleAlways() {
		return firstMatch().whenSimple() == When.ALWAYS;
	}

	/**
	 * Match parts remaning if we subract leading ({@link #firstMatch()}) match. We need this to
	 * consume subsequent chars after lexer dispatch on a first char.
	 */
	@Lazy
	Vect<MatchPart> rest() {
		if (isFirstSimpleAlways()) {
			return parts().<Vect<MatchPart>>when()
					.head((first, rest) -> {
						Optional<MatchPart> withoutFirst = first.dropFirst();
						return withoutFirst.isPresent()
								? rest.prepend(withoutFirst.get())
								: rest;
					})
					.get();
		}
		return parts();
	}

	@Override
	public String toString() {
		return id() + " ~ " + Joiner.on(" ").join(parts())
				+ (and().isPresent() ? " & " + and().get() : "")
				+ (not().isPresent() ? " ! " + not().get() : "");
	}

	static class Builder extends ImmutableTermExpansion.Builder {
		private int index;

		Builder add(CodepointMatch match, Cardinality cardinality) {
			return addParts(MatchPart.of(index++, match, cardinality));
		}
	}

	static Builder builder() {
		return new Builder();
	}

	@Immutable
	static abstract class MatchPart {
		abstract @Parameter int index();
		abstract @Parameter CodepointMatch match();
		abstract @Parameter Cardinality cardinality();

		abstract MatchPart withCardinality(Cardinality cardinality);
		abstract MatchPart withMatch(CodepointMatch sequence);

		static MatchPart of(int index, CodepointMatch match, Cardinality cardinality) {
			return ImmutableTermExpansion.MatchPart.of(index, match, cardinality);
		}

		Optional<MatchPart> dropFirst() {
			assert cardinality().isAtLeastOne();

			if (match().sequence() != null) {
				assert cardinality().isExactlyOne();

				if (match().sequence().points.size() == 1) {
					return Optional.empty();
				}
				return Optional.of(withMatch(
						new CodepointMatch.Sequence(match().sequence().points.rangeFrom(1))));
			}
			if (cardinality().isExactlyOne()) {
				return Optional.empty();
			}
			return Optional.of(withCardinality(Cardinality.C0_N));
		}

		@Override
		public String toString() {
			return "" + match() + cardinality();
		}
	}

	static Vect<TermExpansion> collectFrom(Unit unit) {
		Collecter collecter = new Collecter();
		collecter.toUnit(unit);
		return collecter.result();
	}

	private static class Collecter extends GrammarsTransformer {
		final Set<Literal> inlines = Sets.newLinkedHashSet();
		final Map<Literal, TermExpansion> expansions = Maps.newLinkedHashMap();
		Identifier currentKind = TermExpansion.KIND_DEFAULT;

		/** Collecting inline term literals. */
		@Override
		protected ProductionPart asProductionPart(LiteralPart value) {
			inlines.add(value.literal());
			return value;
		}

		@Override
		public LexicalKind toLexicalKind(LexicalKind value) {
			currentKind = value.kind();
			return value;
		}

		/** Collecting explicit term expansions. */
		@Override
		public LexicalTerm toLexicalTerm(LexicalTerm value) {
			expansions.put(value.id(), new Creator(currentKind).read(value));
			return value;
		}

		/** Result is all term with explicit or implicit expansions */
		Vect<TermExpansion> result() {
			for (Literal inline : inlines) {
				expansions.computeIfAbsent(inline,
						literal -> TermExpansion.builder()
								.id(literal)
								.add(new CodepointMatch.Sequence(literal.value()), Cardinality.C1_1)
								.build());
			}

			return Vect.from(expansions.values());
		}

	}

	private static class Creator extends GrammarsTransformer {
		final TermExpansion.Builder termBuilder = TermExpansion.builder();
		final Identifier kind;

		Creator(Identifier kind) {
			this.kind = kind;
		}

		TermExpansion read(LexicalTerm value) {
			termBuilder.id(value.id());
			termBuilder.kind(kind);
			try {
				toLexicalTerm(value);
			} catch (RuntimeException ex) {
				throw new RuntimeException("Term cannot be read " + value.id() + ", parsed data " + value, ex);
			}
			return termBuilder.build();
		}

		@Override
		public CharSeq toCharSeq(CharSeq value) {
			termBuilder.add(sequenceMatchFrom(value), value.cardinality());
			return value;
		}

		@Override
		protected TermPart asLexicalTermNot(LexicalTerm value, TermPart not) {
			termBuilder.not(matchFrom(not));
			return not;
		}

		@Override
		protected TermPart asLexicalTermAnd(LexicalTerm value, TermPart and) {
			termBuilder.and(matchFrom(and));
			return and;
		}

		@Override
		public CharRange toCharRange(CharRange value) {
			CodepointMatch match = rangeMatchFrom(value);
			termBuilder.add(match, value.cardinality());
			return value;
		}

		private CodepointMatch matchFrom(TermPart not) throws AssertionError {
			CodepointMatch rangeMatch;
			if (not instanceof CharRange) {
				rangeMatch = rangeMatchFrom((CharRange) not);
			} else if (not instanceof CharSeq) {
				rangeMatch = sequenceMatchFrom((CharSeq) not);
			} else throw Unreachable.exhaustive();
			return rangeMatch;
		}

		private Sequence sequenceMatchFrom(CharSeq value) {
			return new CodepointMatch.Sequence(value.literal().value());
		}

		private CodepointMatch rangeMatchFrom(CharRange value) {
			CodepointMatch.Builder matchBuilder = CodepointMatch.builder();
			for (CharRangeElement element : value.elements()) {
				if (element.to().isPresent()) {
					matchBuilder.add(
							element.negated(),
							codepointFrom(element.from()),
							codepointFrom(element.to().get()));
				} else {
					matchBuilder.add(
							element.negated(),
							codepointFrom(element.from()));
				}
			}
			return matchBuilder.build();
		}

		private Codepoint codepointFrom(Char ch) {
			if (ch instanceof CharLiteral) {
				return Codepoint.fromCodepoint(((CharLiteral) ch).value());
			} else if (ch instanceof CharEscapeSpecial) {
				return Codepoint.fromEscape(((CharEscapeSpecial) ch).value());
			} else if (ch instanceof CharEscapeUnicode) {
				return Codepoint.of(Integer.parseInt(((CharEscapeUnicode) ch).value(), 16));
			} else {
				throw Unreachable.exhaustive();
			}
		}
	}

	private static Identifier toSyntheticIdentifier(Literal literal) {
		String s = literal.value();

		// slightly better looking identifier if contains allowed java identifier char
		if (s.codePoints().allMatch(Character::isJavaIdentifierPart)) {
			return Identifier.of("t_" + s);
		}

		// leave only java identifier chars as descriptive suffix
		int[] codepoints = s.codePoints()
				.flatMap(p -> Codepoint.of(p).toString().codePoints())
				.filter(c -> Character.isJavaIdentifierPart(c))
				.toArray();

		String suffix = new String(codepoints, 0, codepoints.length);
		String prefix = "t" + literal.hashCode() + "_";
		return Identifier.of(prefix + suffix);
	}

	static final Identifier KIND_DEFAULT = Identifier.of("_");
	static final Identifier KIND_IGNORED = Identifier.of("ignored");
}
