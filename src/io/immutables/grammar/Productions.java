package io.immutables.grammar;

import com.google.common.base.Strings;
import com.google.common.primitives.Shorts;

/**
 * Productions coded as flat tree encoded in {@code long} array.
 * <ul>
 * <li>Each production or term occupies 2 subsequent longs.
 * <li>Each production or term contains
 * <li>long1 bits 0-32: index of next sibling for easy skipping.
 * <li>long1 bits 32-48: code of field/part of parent production.
 * <li>long1 bits 48-64: code for production/term, for symbols positive value users, for
 * productions - negative values
 * <li>long2 bits 0-32: begin token index in terms storage
 * <li>long2 bits 32-64: end token index in terms storage
 * </ul>
 * <p>
 */
public abstract class Productions {
	private final long[] elements;
	/** Exclusive end position. */
	private final int endPosition;
	private final Terms terms;
	private final int mismatchAt;
	private final int mismatchTermActual;
	private final int mismatchTermExpected;
	private final short mismatchProduction;
	private final boolean completed;

	protected Productions(Terms terms, Parser parser) {
		this.terms = terms;
		this.elements = parser.elements;
		this.endPosition = parser.position;
		this.completed = parser.checkCompleted();
		this.mismatchAt = parser.mismatchAt;
		this.mismatchTermActual = parser.mismatchTermActual;
		this.mismatchTermExpected = parser.mismatchTermExpected;
		this.mismatchProduction = parser.mismatchProduction;
	}

	public String show() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < endPosition; i += 2) {
			long l1 = elements[i];
			long l2 = elements[i + 1];
			int nextSibling = decodeNextSibling(l1);
			short kind = decodeKind(l1);
			short part = decodePart(l1);
			int termBegin = decodeTermBegin(l2);
			int termEnd = decodeTermEnd(l2);

			builder.append(Strings.padStart(Integer.toHexString(i), 4, '0'))
					.append("—")
					.append(Strings.padStart(Integer.toHexString(nextSibling), 4, '0'))
					.append("| ")
					.append(Strings.padEnd((part >= 0 ? showPart(part) + ":" : "*:") + showKind(kind), 20, ' '))
					.append(" |")
					.append(l2 < 0 ? "????" : terms.rangeInclusive(termBegin, termEnd).get())
					.append("\n");
		}
		return builder.toString();
	}

	public abstract String showKind(short kind);

	public abstract String showPart(short part);

	public abstract class Traversal {
		@Override
		public String toString() {
			return super.toString();
		}
	}

	public static abstract class Parser {
		private static final int NO_MISMATCH = -1;
		protected final Terms.Traversal terms;
		// TODO maybe smartly reuse segments
		protected long[] elements = ZERO_LONG_ARRAY;
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

		public boolean checkCompleted() {
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

			long l1 = elements[positionBegin];
			long l2 = elements[positionBegin + 1];

			int termEnd = terms.index();

			l1 = encodeNextSibling(l1, position);
			l2 = encodeTermEnd(l2, termEnd);

			elements[positionBegin] = l1;
			elements[positionBegin + 1] = l2;

			return true;
		}

		protected final boolean term(short part, int term) {
			int i = terms.index();
			int t = terms.advance();
			if (t == term) {
				markTermBegin();
				if (part != NO_PART) {
					markTerm(part, t);
				}
				return true;
			}
			mismatch(terms.index(), term, t);
			terms.reset(i);
			return false;
		}

		private void mismatch(int index, int termExpected, int termActual) {
			// farthest or first (when equal) mismatch wins
			if (index > mismatchAt) {
				mismatchAt = index;
				mismatchTermActual = termActual;
				mismatchTermExpected = termExpected;
				mismatchProduction = currentProduction;
			}
		}

		private final void markTermBegin() {
			int index = terms.index();
			// Climbing upwards to backfill begin term for productions not yet marked with such.
			// We expect that any preceeding sibling would already have been marked
			// by the same routine. So this bubbling will always occur for the
			// first sibling up to each parent recursively up when necessary
			// Here we iteration backwards over each second long
			for (int p = position - 1; p > 0; p -= POSITION_INCREMENT) {
				// Stop if already marked with begin token
				// Everything is already marked upwards and backwards
				if (elements[p] >= 0) break;
				elements[p] = encodeTermBegin(0L, index);
			}
			// clearing mismatch if we succeeded further
			if (index > mismatchAt) {
				// we expect either exhaust input or fail even further
				// if not doing this we're can report problem in the wrong place
				// for the case where we have successfully matched everything
				// but have unmatching trailing input
				mismatchAt = NO_MISMATCH;
				mismatchProduction = 0;
			}
		}

		private final boolean markTerm(short part, int term) {
			long l1 = 0, l2 = 0;
			int p = position;
			int index = terms.index();

			l1 = encodePart(l1, part);
			l1 = encodeKind(l1, Shorts.checkedCast(term)); // term is positive, productions negative
			l1 = encodeNextSibling(l1, p + POSITION_INCREMENT);
			l2 = encodeTermBegin(l2, index);
			l2 = encodeTermEnd(l2, index);

			elements = Capacity.ensure(elements, p, POSITION_INCREMENT);
			elements[position] = l1;
			elements[position + 1] = l2;

			position += POSITION_INCREMENT;

			return true;
		}

		private static final int POSITION_INCREMENT = 2;
		private static final long[] ZERO_LONG_ARRAY = new long[0];
	}

	public boolean ok() {
		return terms.ok() && completed;
	}

	public CharSequence message() {
		return ok() ? "" : buildDiagnosticMessage();
	}

	private CharSequence buildDiagnosticMessage() {
		if (terms.hasUnexpected()) return buildUnexpectedMessage();
		if (hasUnconsumed()) return buildUnconsumedMessage();
		return buildMismatchMessage();
	}

	private boolean hasUnconsumed() {
		return mismatchAt >= 0 && mismatchProduction == 0;
	}

	private CharSequence buildUnconsumedMessage() {
		Source.Range range = terms.range(mismatchAt);
		return range.begin()
				+ " Unexpected terms starting with `" + range.get() + "` "
				+ "\n\t" + range.highlight().toString().replace("\n", "\n\t")
				+ "Unconsumed terms which are not forming any construct";
	}

	private CharSequence buildUnexpectedMessage() {
		Source.Range range = terms.firstUnexpectedRange();
		return range.begin()
				+ " Unexpected characters `" + range.get() + "`"
				+ "\n\t" + range.highlight().toString().replace("\n", "\n\t")
				+ "Characters are not forming any recognized token";
	}

	private CharSequence buildMismatchMessage() {
		Source.Range range = terms.range(mismatchAt);
		return range.begin()
				+ " Stumbled on `" + range.get() + "`"
				+ terms.showTerm(mismatchTermActual) + " while expecting " + terms.showTerm(mismatchTermExpected)
				+ " term in/after " + showKind(mismatchProduction) + ""
				+ "\n\t" + range.highlight().toString().replace("\n", "\n\t")
				+ "Cannot parse production because of mismatched term";
	}

	public static final short ANY_PART = (short) 0xffff;
	public static final short NO_PART = (short) 0x0000;

	static int decodeNextSibling(long l1) {
		return (int) l1;
	}

	static short decodePart(long l1) {
		return (short) (l1 >> Integer.SIZE);
	}

	static short decodeKind(long l1) {
		return (short) (l1 >> (Integer.SIZE + Short.SIZE));
	}

	static int decodeTermBegin(long l2) {
		return (int) l2;
	}

	static int decodeTermEnd(long l2) {
		return (int) (l2 >> Integer.SIZE);
	}

	static long encodeNextSibling(long l1, int position) {
		return l1 | Integer.toUnsignedLong(position);
	}

	static long encodePart(long l1, short part) {
		return l1 | (Short.toUnsignedLong(part) << Integer.SIZE);
	}

	static long encodeKind(long l1, short kind) {
		return l1 | (Short.toUnsignedLong(kind) << (Integer.SIZE + Short.SIZE));
	}

	static long encodeTermBegin(long l2, int tokenIndex) {
		return l2 | Integer.toUnsignedLong(tokenIndex);
	}

	static long encodeTermEnd(long l2, int tokenIndex) {
		return l2 | (Integer.toUnsignedLong(tokenIndex) << Integer.SIZE);
	}
}
