package io.immutables.grammar;

import com.google.common.base.Strings;
import com.google.common.primitives.Shorts;
import io.immutables.Capacity;
import java.util.NoSuchElementException;

/**
 * Productions coded as flat tree encoded in {@code long} array.
 * <ul>
 * <li>Each production or term occupies 2 subsequent longs.
 * <li>Each production or term contains
 * <li>long1 bits 0-32: index increment (offset) of next sibling for easy skipping.
 * <li>long1 bits 32-48: code of field/part of parent production.
 * <li>long1 bits 48-64: code for production/term, for symbols positive value users, for
 * productions - negative values
 * <li>long2 bits 0-32: begin token index in terms storage
 * <li>long2 bits 32-64: end token index in terms storage
 * </ul>
 * <p>
 */
public abstract class Productions<K, T extends TreeProduction<K>> {
	private final long[] elements;
	/** Exclusive end position. */
	private final int endPosition;
	private final Terms terms;
	private final int mismatchAt;
	private final int mismatchTermActual;
	private final int mismatchTermExpected;
	private final short mismatchProduction;
	private final boolean completed;
	private final TreeConstructor<T> constructor;

	protected Productions(Terms terms, Parser parser, TreeConstructor<T> constructor) {
		this.terms = terms;
		this.constructor = constructor;
		this.elements = parser.elements;
		this.endPosition = parser.position;
		this.completed = parser.checkCompleted();
		this.mismatchAt = parser.mismatchAt;
		this.mismatchTermActual = parser.mismatchTermActual;
		this.mismatchTermExpected = parser.mismatchTermExpected;
		this.mismatchProduction = parser.mismatchProduction;
	}

	@FunctionalInterface
	protected interface TreeConstructor<T> {
		T construct(Traversal traversal);
	}

	public final T construct() {
		Traversal traverse = traverse();
		Traversal.At at = traverse.next();
		assert at != Traversal.At.EOP;
		T result = constructor.construct(traverse);
		at = traverse.next();
		assert at == Traversal.At.EOP;
		return result;
	}

	public final String show() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < endPosition; i += 2) {
			appendPosition(builder, i);
			builder.append('\n');
		}
		return builder.toString();
	}

	private void appendPosition(StringBuilder builder, int position) {
		if (position >= endPosition || position < 0) {
			builder.append(Strings.padStart(Integer.toHexString(position), 4, '0'))
					.append(Strings.repeat("—", 5))
					.append(" +")
					.append(Strings.padStart("", 4, '0'))
					.append("| ");
			return;
		}

		long l0 = elements[position];
		long l1 = elements[position + 1];
		int nextIncrement = decodeLength(l0);
		int nextSibling = position + nextIncrement;
		short kind = decodeKind(l0);
		short part = decodePart(l0);
		int termBegin = decodeTermBegin(l1);
		int termEnd = decodeTermEnd(l1);

		builder.append(Strings.padStart(Integer.toHexString(position), 4, '0'))
				.append("—")
				.append(Strings.padStart(Integer.toHexString(nextSibling), 4, '0'))
				.append(" +")
				.append(Strings.padStart(Integer.toHexString(nextIncrement), 4, '0'))
				.append("| ")
				.append(Strings.padEnd((part >= 0 ? showPart(part) + ":" : "*:") + showKind(kind), 20, ' '))
				.append(" |")
				.append(l1 < 0 ? "????" : terms.rangeInclusive(termBegin, termEnd).get(terms.source()));
	}

	public abstract String showKind(short kind);

	public abstract String showPart(short part);

	protected final String showTerm(short term) {
		return terms.showTerm(term);
	}

	public final Traversal traverse() {
		return new Traversal(this);
	}

	/**
	 * Pull-style traversal over productions.
	 * Mechanic is to be revised
	 */
	public static final class Traversal {
		public enum At {
			PRODUCTION_BEGIN, PRODUCTION_END, TERM, EOP
		}

		private final Productions<?, ?> productions;
		private final long[] elements;
		private final int endPosition;

		private At current = At.EOP;
		private int position = -POSITION_INCREMENT;
		// should be fine enouph to avoid initial reallocation.
		private int[] stackEnds = new int[32];
		private int[] stackPositions = new int[32];
		private int stackPointer = -1;

		private Traversal(Productions<?, ?> productions) {
			this.productions = productions;
			this.elements = productions.elements;
			this.endPosition = productions.endPosition;
		}

		public At current() {
			return current;
		}

		public At next() {
			// Give away any stacked PRODUCTION_ENDs at next position
			if (stackPointer >= 0 && stackEnds[stackPointer] == position + POSITION_INCREMENT) {
				stackPointer--;
				return current = At.PRODUCTION_END;
			}

			position += POSITION_INCREMENT;

			if (position >= endPosition) {
				return current = At.EOP;
			}

			long l0 = elements[position];
			int nextSibling = position + decodeLength(l0);
			boolean isTerm = decodeKind(l0) >= 0;

			if (isTerm) {
				return current = At.TERM;
			}

			// if not term (when we're suppose to return PRODUCTION_BEGIN
			// we stack END_PROD to be returned
			stackPointer++;
			stackEnds = Capacity.ensure(stackEnds, stackPointer, 1);
			stackPositions = Capacity.ensure(stackPositions, stackPointer, 1);
			stackEnds[stackPointer] = nextSibling;
			stackPositions[stackPointer] = position;

			return current = At.PRODUCTION_BEGIN;
		}

		public void skip() {
			assert productionBeginOrTerm();
			if (current != At.TERM) {
				position += decodeLength(elements[position]) - POSITION_INCREMENT;
			}
		}

		private boolean productionBeginOrTerm() {
			return current == At.PRODUCTION_BEGIN || current == At.TERM;
		}

		public short kind() {
			assert productionBeginOrTerm();
			return decodeKind(elements[position]);
		}

		public short part() {
			assert productionBeginOrTerm();
			return decodePart(elements[position]);
		}

		public int termBegin() {
			assert productionBeginOrTerm();
			return decodeTermBegin(elements[position + 1]);
		}

		public int termEnd() {
			assert productionBeginOrTerm();
			return decodeTermEnd(elements[position + 1]);
		}

		public Source.Range range() {
			return productions.terms.rangeInclusive(termBegin(), termEnd());
		}

		public CharSequence source() {
			return productions.terms.source();
		}

		public Symbol term() {
			return Symbol.from(range().get(productions.terms.source()));
		}

		public String show() {
			StringBuilder b = new StringBuilder();
			productions.appendPosition(b, referencePosition());
			return b.append(" // ").append(current).toString();
		}

		private int referencePosition() {
			if (current == At.PRODUCTION_END) {
				// if we just returned production end, then me moved stack pointer below,
				// but the stack itself still has position entry not overwritten
				return stackPositions[stackPointer + 1];
			}
			return position;
		}

		@Override
		public String toString() {
			String where;
			if (position < 0) {
				where = "not started";
			} else if (position >= endPosition) {
				where = "end";
			} else {
				where = current + ":" + position;
			}
			return Productions.class.getSimpleName()
					+ '.' + Traversal.class.getSimpleName()
					+ '(' + where + ')';
		}
	}

	protected static abstract class Parser {
		private static final int NO_MISMATCH = -1;
		protected final Terms.Traversal terms;
		// TODO maybe reuse smartly segmented arrays?
		protected long[] elements = EMPTY_LONG_ARRAY;
		protected int position = 0;

		int mismatchAt = NO_MISMATCH;
		int mismatchTermActual;
		int mismatchTermExpected;
		short mismatchProduction;
		short currentProduction;

		protected Parser(Terms input) {
			this.terms = input.traverse();
			this.elements = Capacity.ensure(elements, 0, input.count() / 4); // XXX approximation ok?
		}

		boolean checkCompleted() {
			// if we're reached EOF, then we're done
			if (terms.advance() == Terms.EOF) return true;
			if (mismatchAt == NO_MISMATCH) {
				// if no mismatch recorded but we have unconsumed input
				// we set mismatchAt to first uncosumed position
				mismatchAt = terms.index();
			}
			return false;
		}

		protected final void production(short part, short kind) {
			if (part == NO_PART) return;
			currentProduction = kind;

			long l1 = 0;
			l1 = encodePart(l1, part);
			l1 = encodeKind(l1, kind);

			elements = Capacity.ensure(elements, position, POSITION_INCREMENT);
			elements[position] = l1;
			// reset to undefined this is important because of how
			// we check it in markTermBegin and because there may have
			// left some junk from failed match attempts
			elements[position + 1] = -1L;

			position += POSITION_INCREMENT;
		}

		protected final boolean end(short part, int positionBegin) {
			if (part == NO_PART) return true;

			long l0 = elements[positionBegin];
			long l1 = elements[positionBegin + 1];

			int termEnd = terms.index();

			l0 = encodeLength(l0, position - positionBegin);
			l1 = encodeTermEnd(l1, termEnd);

			elements[positionBegin] = l0;
			elements[positionBegin + 1] = l1;

			return true;
		}

		protected final boolean term(short part, int term) {
			int i = terms.index();
			int t = terms.advance();
			if (t == term) {
				match(part, t);
				return true;
			}
			mismatch(term, t);
			terms.reset(i);
			return false;
		}

		protected final void match(short part, int term) {
			markTermBegin();
			if (part != NO_PART) {
				markTerm(part, term);
			}
		}

		protected final void mismatch(int termExpected, int termActual) {
			int index = terms.index();
			// farthest or first (when equal) mismatch wins
			if (index > mismatchAt) {
				mismatchAt = index;
				mismatchTermActual = termActual;
				mismatchTermExpected = termExpected;
				mismatchProduction = currentProduction;
			}
		}

		private void markTermBegin() {
			int index = terms.index();
			// Climbing upwards to backfill begin term for productions not yet marked with such.
			// We expect that any preceeding sibling would already have been marked
			// by the same routine. So this bubbling will always occur for the
			// first sibling up to each parent up until no longer necessary.
			// Here we iterate backwards over each second long
			for (int p = position - 1; p > 0; p -= POSITION_INCREMENT) {
				// Stop if already marked with begin token
				// Everything is already marked upwards and backwards
				if (elements[p] >= 0) break;
				elements[p] = encodeTermBegin(0L, index);
			}
			// clearing mismatch if we succeeded any further
			if (index > mismatchAt) {
				// we expect either exhaust input or fail even further.
				// when not doing this we're reporting a problem in the wrong place
				// for the case where we have successfully matched everything,
				// yet have unmatching trailing input
				mismatchAt = NO_MISMATCH;
				mismatchProduction = 0;
			}
		}

		private boolean markTerm(short part, int term) {
			int p = position;
			int index = terms.index();

			long l0 = 0, l1 = 0;
			l0 = encodePart(l0, part);
			l0 = encodeKind(l0, Shorts.checkedCast(term)); // term is positive, productions negative
			l0 = encodeLength(l0, POSITION_INCREMENT);
			l1 = encodeTermBegin(l1, index);
			l1 = encodeTermEnd(l1, index);

			elements = Capacity.ensure(elements, p, POSITION_INCREMENT);
			elements[position] = l0;
			elements[position + 1] = l1;

			position += POSITION_INCREMENT;

			return true;
		}
	}

	/** the total number of parsed productions and terms. */
	public final int length() {
		return endPosition / POSITION_INCREMENT;
	}

	/** If parsing input succeeded. */
	public final boolean ok() {
		return terms.ok() && completed;
	}

	public final String message() {
		if (ok()) return "ok";
		if (terms.hasUnexpected()) return buildUnexpectedMessage();
		if (hasUnconsumed()) return buildUnconsumedMessage();
		return buildMismatchMessage();
	}

	public final String messageForFile(String file) {
		return file + ":" + message();
	}

	@Override
	public final String toString() {
		return getClass().getSimpleName() + "(" + length() + ")";
	}

	public final boolean hasUnmatched() {
		return mismatchAt >= 0;
	}

	public final Source.Range getUnmatched() {
		if (!hasUnmatched()) throw new NoSuchElementException();
		return terms.range(mismatchAt);
	}

	private boolean hasUnconsumed() {
		return mismatchAt >= 0 && mismatchProduction == 0;
	}

	private String buildUnconsumedMessage() {
		Source.Range range = terms.range(mismatchAt);
		return range.begin
				+ " Unexpected terms starting with `" + range.get(terms.source()) + "` "
				+ "\n\t" + terms.highlight(range).toString().replace("\n", "\n\t")
				+ "Unconsumed terms which are not forming any construct";
	}

	private String buildUnexpectedMessage() {
		Source.Range range = terms.firstUnexpectedRange();
		return range.begin
				+ " Unexpected characters `" + range.get(terms.source()) + "`"
				+ "\n\t" + terms.highlight(range).toString().replace("\n", "\n\t")
				+ "Characters are not forming any recognized token";
	}

	private String buildMismatchMessage() {
		Source.Range range = terms.range(mismatchAt);
		return range.begin
				+ " Stumbled on `" + range.get(terms.source()) + "`"
				+ terms.showTerm(mismatchTermActual) + " while expecting " + terms.showTerm(mismatchTermExpected)
				+ " term in/after " + showKind(mismatchProduction) + ""
				+ "\n\t" + terms.highlight(range).toString().replace("\n", "\n\t")
				+ "Cannot parse production because of mismatched term";
	}

	static int decodeLength(long l0) {
		return (int) l0;
	}

	static short decodePart(long l0) {
		return (short) (l0 >> Integer.SIZE);
	}

	static short decodeKind(long l0) {
		return (short) (l0 >> (Integer.SIZE + Short.SIZE));
	}

	static int decodeTermBegin(long l1) {
		return (int) l1;
	}

	static int decodeTermEnd(long l1) {
		return (int) (l1 >> Integer.SIZE);
	}

	static long encodeLength(long l0, int positionIncrement) {
		return l0 | Integer.toUnsignedLong(positionIncrement);
	}

	static long encodePart(long l0, short part) {
		return l0 | (Short.toUnsignedLong(part) << Integer.SIZE);
	}

	static long encodeKind(long l0, short kind) {
		return l0 | (Short.toUnsignedLong(kind) << (Integer.SIZE + Short.SIZE));
	}

	static long encodeTermBegin(long l1, int tokenIndex) {
		return l1 | Integer.toUnsignedLong(tokenIndex);
	}

	static long encodeTermEnd(long l1, int tokenIndex) {
		return l1 | (Integer.toUnsignedLong(tokenIndex) << Integer.SIZE);
	}

	private static final int POSITION_INCREMENT = 2;
	private static final long[] EMPTY_LONG_ARRAY = {};

	protected static final short ANY_PART = (short) 0xffff;
	protected static final short NO_PART = (short) 0x0000;
}
