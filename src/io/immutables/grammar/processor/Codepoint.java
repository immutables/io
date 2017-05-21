package io.immutables.grammar.processor;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.primitives.Chars;
import io.immutables.grammar.Escapes;
import javax.annotation.Nullable;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Immutable
abstract class Codepoint implements Comparable<Codepoint> {
	@Parameter
	abstract int value();

	boolean isSimple() {
		return value() < END_SIMPLE;
	}

	boolean isSimpleLetter() {
		return isSimple() && LETTER.matches((char) value());
	}

	@Check
	void check() {
		checkState(inCodepointRange(value()));
	}

	private static boolean inCodepointRange(int value) {
		return value >= MIN_VALUE && value <= MAX_VALUE;
	}

	String toCharString() {
		return String.valueOf(Chars.saturatedCast(value()));
	}

	@Override
	public String toString() {
		return Escapes.escaper().escape(toCharString());
	}

	String toStringInRange() {
		return Escapes.escaperRange().escape(toCharString());
	}

	@Override
	public int compareTo(Codepoint o) {
		return Integer.compare(value(), o.value());
	}

	static Codepoint of(int value) {
		checkArgument(inCodepointRange(value));
		if (value < END_SIMPLE) {
			return Simples.TABLE[value - MIN_VALUE];
		}
		return ImmutableCodepoint.of(value);
	}

	static Codepoint fromCodepoint(String symbol) {
		checkArgument(symbol.codePointCount(0, symbol.length()) == 1);
		return of(symbol.codePointAt(0));
	}

	static Codepoint fromEscape(String symbol) {
		String errorMessage = "Expect a single char, one of: nrtfbs'\" but was: ";
		checkArgument(symbol.length() == 1, errorMessage + symbol);
		@Nullable Character c = Escapes.unescapesRange().get("\\" + symbol.charAt(0));
		checkArgument(c != null, errorMessage + symbol);
		return of(c.charValue());
	}

	private static class Domain extends DiscreteDomain<Codepoint> {
		static final Domain INSTANCE = new Domain();

		private Domain() {}

		@Override
		public Codepoint next(Codepoint value) {
			return Codepoint.of(value.value() + 1);
		}

		@Override
		public Codepoint previous(Codepoint value) {
			return Codepoint.of(value.value() - 1);
		}

		@Override
		public long distance(Codepoint start, Codepoint end) {
			return end.value() - start.value();
		}

		@Override
		public Codepoint maxValue() {
			return Codepoint.of(MAX_VALUE);
		}

		@Override
		public Codepoint minValue() {
			return Codepoint.of(MIN_VALUE);
		}
	}

	static DiscreteDomain<Codepoint> domain() {
		return Domain.INSTANCE;
	}

	private static final int END_SIMPLE = 128;
	private static final int MIN_VALUE = 0;
	private static final int MAX_VALUE = Character.MAX_CODE_POINT;

	static final Range<Codepoint> SIMPLE_RANGE =
			Range.closedOpen(Codepoint.of(MIN_VALUE), Codepoint.of(END_SIMPLE));

	/** here simple means ascii */
	static final ContiguousSet<Codepoint> SIMPLE_SET = ContiguousSet.create(SIMPLE_RANGE, domain());

	static final String SPECIAL_CHARS = "!\"#$%&'()*+,-./:;<=>?@[]^_{|}~";
	static final CharMatcher RANGE_UNSAFE = CharMatcher.anyOf("[]^-\\");

	private static final CharMatcher LETTER =
			CharMatcher.inRange('a', 'z')
					.or(CharMatcher.inRange('A', 'Z'))
					.precomputed();

	private static class Simples {
		static final Codepoint[] TABLE = new Codepoint[END_SIMPLE - MIN_VALUE];
		static {
			for (int v = MIN_VALUE; v < END_SIMPLE; v++) {
				TABLE[v - MIN_VALUE] = ImmutableCodepoint.of(v);
			}
		}
	}
}
