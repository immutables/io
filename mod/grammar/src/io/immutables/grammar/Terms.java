package io.immutables.grammar;

import io.immutables.Capacity;
import io.immutables.Source;
import java.util.NoSuchElementException;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public abstract class Terms {
	private final CharSequence source;
	private final Source.Lines lines;
	private final char[] input;
	private final int[] tokens;
	private final int tokenEnd;
	private final int unexpectedAt;

	protected Terms(Tokenizer tokenizer) {
		this.input = tokenizer.input;
		this.tokens = tokenizer.tokens;
		this.tokenEnd = tokenizer.index;
		this.lines = tokenizer.lines.lines(this.input.length);
		this.unexpectedAt = tokenizer.firstUnexpectedAt;
		this.source = Source.wrap(input);
	}

	protected abstract int kindTerm(int term);
	protected abstract String showTerm(int term);
	protected abstract Traversal newTraversal(int[] tokens, int tokenEnd);

	public final boolean ok() {
		return !hasUnexpected();
	}

	public final boolean hasUnexpected() {
		return unexpectedAt >= 0;
	}

	public final CharSequence source() {
		return source;
	}

	public StringBuilder highlight(Source.Range range) {
		return Source.Excerpt.from(source, lines).get(range);
	}

	/** Number of terms. */
	public final int count() {
		return tokenEnd >> 1;
	}

	private final int beforePosition(int index) {
		return index <= 0 ? 0 : tokens[index - 1];
	}

	private final int afterPosition(int index) {
		if (index + 1 >= tokens.length) {
			return input.length;
		}
		return tokens[index + 1];
	}

	public final Source.Range range(int index) {
		return rangePositions(
				beforePosition(index),
				afterPosition(index));
	}

	public final Source.Range rangeInclusive(int fromIndex, int toIndex) {
		return rangePositions(
				beforePosition(fromIndex),
				afterPosition(toIndex));
	}

	private Source.Range rangePositions(int beforePosition, int afterPosition) {
		Source.Position begin = lines.get(beforePosition);
		if (afterPosition > beforePosition) {
			Source.Position end = lines.get(afterPosition);
			return Source.Range.of(begin, end);
		}
		return Source.Range.of(begin);
	}

	public Source.Range firstUnexpectedRange() {
		if (!hasUnexpected()) throw new NoSuchElementException();
		return range(unexpectedAt);
	}

	public String show() {
		StringBuilder b = new StringBuilder();

		Traversal traversal = traverse();
		for (int t; (t = traversal.next()) != EOF;) {
			int before = traversal.beforePosition();
			int after = traversal.afterPosition();
			String range = String.valueOf(input, before, after - before);
			String escapedRange = Escapes.escaperRange().escape(range);
			b.append(showTerm(t)).append(escapedRange).append(' ');
		}

		b.setLength(Math.max(0, b.length() - 1)); // cut out trailing space
		return b.toString();
	}

	public final Traversal traverse() {
		return newTraversal(tokens, tokenEnd);
	}

	@NotThreadSafe
	public abstract class Traversal {
		private final int[] tokens;
		private final int tokenEnd;
		private int index = -INDEX_STEP;

		protected Traversal(int[] tokens, int tokenEnd) {
			this.tokens = tokens;
			this.tokenEnd = tokenEnd;
		}

		public abstract int advance();

		public final void reset(int index) {
			this.index = index;
		}

		public int index() {
			return index;
		}

		public int next() {
			index += INDEX_STEP;
			if (index >= tokenEnd) return EOF;
			return tokens[index];
		}

		public int current() {
			if (index >= tokenEnd) return EOF;
			return index <= 0 ? EOF : tokens[index];
		}

		public int beforePosition() {
			return Terms.this.beforePosition(index);
		}

		public int afterPosition() {
			return Terms.this.afterPosition(index);
		}

		@Deprecated
		public Symbol getSymbol() {
			return Symbol.from(input, beforePosition(), afterPosition());
		}

		public Source.Range getCurrentRange() {
			return range(index);
		}

		@Override
		public String toString() {
			return Terms.this.getClass().getSimpleName()
					+ "." + Traversal.class.getSimpleName() + getCurrentRange();
		}
	}

	protected static abstract class Tokenizer {
		private final Source.Lines.Tracker lines = new Source.Lines.Tracker();

		private final char[] input;
		protected int position = -1; // before nextChar

		private int[] tokens = ZERO_INT_ARRAY;
		private int index;
		private int commitedPosition;
		private char current;
		private int firstUnexpectedAt = -1;

		protected Tokenizer(char[] input) {
			this.input = input;
			this.tokens = Capacity.ensure(tokens, 0, input.length / 2); // XXX is good approximation?
		}

		protected abstract int read(char current);

		public final void tokenize() {
			nextChar();
			for (;;) {
				int t = readNext();
				if (t >= 0) continue;
				if (t != UNEXPECTED) break;
				if (firstUnexpectedAt < 0) {
					// track first unexpected for now
					firstUnexpectedAt = index - INDEX_STEP;
				}
			}
			// Track any unconsumed as unexpected
			// (if unrecognized have not been reported before)
			if (position < input.length && firstUnexpectedAt < 0) {
				firstUnexpectedAt = position;
			}
		}

		private int readNext() {
			char c = current;
			if (c == '\0') return EOF;
			return read(c);
		}

		protected final char nextChar() {
			int p = ++position;
			return current = (p < input.length) ? input[p] : '\0';
		}

		protected final int commit(int token) {
			for (int p = this.commitedPosition; p < position; p++) {
				if (input[p] == '\n') {
					lines.addNewlineAt(p);
				}
			}
			this.commitedPosition = position;

			int i = index;
			tokens = Capacity.ensure(tokens, i, INDEX_STEP);
			tokens[i++] = token;
			tokens[i++] = position;
			index = i;
			return token;
		}

		private static final int[] ZERO_INT_ARRAY = new int[0];
	}

	private static final int INDEX_STEP = 2;

	public static final int EOF = -1;
	public static final int UNEXPECTED = -2;
}
