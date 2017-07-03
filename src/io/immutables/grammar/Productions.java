package io.immutables.grammar;

import com.google.common.base.Strings;
import com.google.common.primitives.Shorts;
import java.util.NoSuchElementException;

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
		long l1 = elements[position];
		long l2 = elements[position + 1];
		int nextSibling = decodeNextSibling(l1);
		short kind = decodeKind(l1);
		short part = decodePart(l1);
		int termBegin = decodeTermBegin(l2);
		int termEnd = decodeTermEnd(l2);

		builder.append(Strings.padStart(Integer.toHexString(position), 4, '0'))
				.append("—")
				.append(Strings.padStart(Integer.toHexString(nextSibling), 4, '0'))
				.append("| ")
				.append(Strings.padEnd((part >= 0 ? showPart(part) + ":" : "*:") + showKind(kind), 20, ' '))
				.append(" |")
				.append(l2 < 0 ? "????" : terms.rangeInclusive(termBegin, termEnd).get());
	}

	public abstract String showKind(short kind);

	public abstract String showPart(short part);

	public final Traversal traverse() {
		return new Traversal(this);
	}

	/**
	 * Pull-style traversal over productions.
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
		private int[] stack = new int[32]; // should be fine enouph to avoid initial reallocation.
		private int stackPointer = -1;
		private int prodEndCount = 0;

		private Traversal(Productions<?, ?> productions) {
			this.productions = productions;
			this.elements = productions.elements;
			this.endPosition = productions.endPosition;
		}

		public At current() {
			checkBegin();
			return current;
		}

		public At next() {
			// Give away any enqued/remaining PRODUCTION_ENDs
			if (prodEndCount > 0) {
				prodEndCount--;
				return current = At.PRODUCTION_END;
			}

			position += POSITION_INCREMENT;

			if (position >= endPosition) {
				return current = At.EOP;
			}

			long l1 = elements[position];
			int nextSibling = decodeNextSibling(l1);
			boolean isTerm = decodeKind(l1) >= 0;

			int nextPosition = position + POSITION_INCREMENT;
			// If we're ending on the next position,
			// without having nested nodes
			if (nextSibling == nextPosition) {
				if (!isTerm) {
					// if not term (when we're suppose to return PRODUCTION_BEGIN
					// we enque END_PROD to be returned
					prodEndCount++;
				}
				// Checking if we have have to enque PRODUCTION_ENDs for any
				// stacked productions which end on the same next position
				while (stackPointer >= 0 && stack[stackPointer] == nextPosition) {
					prodEndCount++;
					stackPointer--;
				}
			} else {
				assert !isTerm : "term always have nextSibling == nextPosition";
				// We're having nested production or terms so
				// we stack this position awating for when nextPosition
				// will reach nextSibling so that we'll enque corresponding
				// PRODUCTION_END events
				stackPointer++;
				stack = Capacity.ensure(stack, stackPointer, 1);
				stack[stackPointer] = nextSibling;
			}
			if (isTerm) return current = At.TERM;
			return current = At.PRODUCTION_BEGIN;
		}

		public short kind() {
			checkBegin();
			checkEnd();
			return decodeKind(elements[position]);
		}

		public short part() {
			checkBegin();
			checkEnd();
			return decodePart(elements[position]);
		}

		public int termBegin() {
			checkBegin();
			checkEnd();
			return decodeTermBegin(elements[position + 1]);
		}

		public int termEnd() {
			checkBegin();
			checkEnd();
			return decodeTermEnd(elements[position + 1]);
		}

		public Source.Range range() {
			return productions.terms.rangeInclusive(termBegin(), termEnd());
		}

		public CharSequence source() {
			return productions.terms.source();
		}

		public Symbol term() {
			if (current != At.TERM) throw new NoSuchElementException();
			return Symbol.from(productions.terms.rangeInclusive(termBegin(), termEnd()).get().toString());
		}

		private void checkBegin() {
			if (position < 0) throw new IllegalStateException("Need to call next() first");
		}

		private void checkEnd() {
			if (position >= endPosition) throw new IllegalStateException("");
		}

		public String show() {
			StringBuilder b = new StringBuilder();
			productions.appendPosition(b, position);
			return b.append(" // ").append(current).toString();
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

			long l1 = 0, l2 = 0;
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
		return range.begin()
				+ " Unexpected terms starting with `" + range.get() + "` "
				+ "\n\t" + range.highlight().toString().replace("\n", "\n\t")
				+ "Unconsumed terms which are not forming any construct";
	}

	private String buildUnexpectedMessage() {
		Source.Range range = terms.firstUnexpectedRange();
		return range.begin()
				+ " Unexpected characters `" + range.get() + "`"
				+ "\n\t" + range.highlight().toString().replace("\n", "\n\t")
				+ "Characters are not forming any recognized token";
	}

	private String buildMismatchMessage() {
		Source.Range range = terms.range(mismatchAt);
		return range.begin()
				+ " Stumbled on `" + range.get() + "`"
				+ terms.showTerm(mismatchTermActual) + " while expecting " + terms.showTerm(mismatchTermExpected)
				+ " term in/after " + showKind(mismatchProduction) + ""
				+ "\n\t" + range.highlight().toString().replace("\n", "\n\t")
				+ "Cannot parse production because of mismatched term";
	}

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

	private static final int POSITION_INCREMENT = 2;
	private static final long[] EMPTY_LONG_ARRAY = {};

	protected static final short ANY_PART = (short) 0xffff;
	protected static final short NO_PART = (short) 0x0000;
}
