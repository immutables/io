package io.immutables.grammar;

import io.immutables.grammar.Source.Position;
import java.util.Arrays;
import java.util.NoSuchElementException;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import static com.google.common.base.Preconditions.checkPositionIndex;

@Immutable
public abstract class Terms {
	private final char[] input;
	private final CharSequence source;
	private final int[] tokens;
	private final int tokenEnd;
	private final int[] lines;
	private final int lineCount;
	private final int unexpectedIndex;
	private final boolean prematureEof;

	protected Terms(Lexer lexer) {
		this.input = lexer.input;
		this.tokens = lexer.tokens;
		this.tokenEnd = lexer.index;
		this.lines = lexer.lines;
		this.lineCount = lexer.lineCount;
		this.unexpectedIndex = lexer.firstUnrecognizedIndex;
		this.prematureEof = lexer.hasPrematureEof();
		this.source = new ArrayCharSequence(input);
	}

	protected abstract int kindToken(int token);
	protected abstract String showToken(int token);
	protected abstract Traversal newTraversal(int[] tokens, int tokenEnd);

	public final boolean ok() {
		return !hasUnexpected() && !hasPrematureEof();
	}

	public final boolean hasUnexpected() {
		return unexpectedIndex >= 0;
	}

	public final boolean hasPrematureEof() {
		return prematureEof;
	}

	public final CharSequence getSource() {
		return source;
	}

	private Source.Position toSourcePosition(int position) {
		int lineIndex = Arrays.binarySearch(lines, 0, lineCount, position);
		if (lineIndex < 0) {
			lineIndex = -lineIndex - 2;
		}
		int lineNumber = lineIndex + 1;
		int columnNumber = position - lines[lineIndex];
		return Position.of(position, lineNumber, columnNumber);
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

	public final Source.Range tokenRange(int index) {
		int beforePosition = beforePosition(index);
		int afterPosition = afterPosition(index);

		Source.Range.Builder builder = new Source.Range.Builder();
		builder.source(getSource());
		builder.begin(toSourcePosition(beforePosition));
		if (afterPosition > beforePosition) {
			builder.end(toSourcePosition(afterPosition));
		}
		return builder.build();
	}
	
	public Source.Range getPrematureEof() {
		if (!hasPrematureEof()) throw new NoSuchElementException();
		return tokenRange(tokenEnd);
	}

	public Source.Range getFirstUnrecognized() {
		if (!hasUnexpected()) throw new NoSuchElementException();
		return tokenRange(unexpectedIndex);
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

		public Symbol getSymbol() {
			return Symbol.from(input, beforePosition(), afterPosition());
		}

		public Source.Range getCurrentRange() {
			return tokenRange(index);
		}

		public String show() {
			StringBuilder b = new StringBuilder();

			for (int t; (t = next()) != EOF;) {
				int before = beforePosition();
				int after = afterPosition();
				String range = String.valueOf(input, before, after - before);
				String escapedRange = Escapes.escaperRange().escape(range);
				b.append(showToken(t)).append(escapedRange).append(' ');
			}

			b.setLength(Math.max(0, b.length() - 1)); // cut out trailing space
			return b.toString();
		}

		@Override
		public String toString() {
			return Terms.this.getClass().getSimpleName() + "." + getClass().getSimpleName() + getCurrentRange();
		}
	}

	protected static abstract class Lexer {
		private final char[] input;
		protected int position;

		private int[] tokens = new int[0];
		private int[] lines = new int[128];
		{
			// last position before first line is -1
			// i.e. non-existing zero line ends on -1 position, on the next, 0
			// position first line starts.
			lines[0] = -1;
		}
		// at least one line is available starting from 0 position, even if
		// input is empty
		private int lineCount = 1;
		private int index;
		private int commitedPosition;
		private char current;
		private int firstUnrecognizedIndex;

		protected Lexer(char[] input) {
			this.input = input;
			this.tokens = ensureCapacityFor(tokens, 0, input.length >> 1); // XXX is good approximation?
		}

		protected abstract int readToken(char current);

		public final void tokenize() {
			position = -1; // before nextChar
			firstUnrecognizedIndex = -1;
			nextChar();
			for (;;) {
				int t = readNextToken();
				if (t >= 0) continue;
				if (t == UNRECOGNIZED) {
					if (firstUnrecognizedIndex < 0) {
						// track first unrecognized for now
						firstUnrecognizedIndex = index - INDEX_STEP;
					}
					continue;
				}
				break;
			}
		}

		final boolean hasPrematureEof() {
			return position < input.length;
		}

		private int readNextToken() {
			char c = current;
			if (c == '\0') return EOF;
			return readToken(c);
		}

		protected final char nextChar() {
			int p = ++position;
			return current = (p < input.length) ? input[p] : '\0';
		}

		protected final int commit(int token) {
			for (int p = this.commitedPosition; p < position; p++) {
				char c = input[p];
				if (c == '\n') {
					// recording end of the line
					lines = ensureCapacityFor(lines, lineCount, 1);
					lines[lineCount++] = p;
				}
			}
			this.commitedPosition = position;

			int i = index;
			tokens = ensureCapacityFor(tokens, i, INDEX_STEP);
			tokens[i++] = token;
			tokens[i++] = position;
			index = i;
			return token;
		}

		private static int[] ensureCapacityFor(int[] elements, int limit, int increment) {
			int oldCapacity = elements.length;
			// check is made this way to avoid overflow
			if (oldCapacity - limit < increment) {
				int requiredCapacity = oldCapacity + increment;
				int newCapacity;
				// checking for overflow
				if (requiredCapacity < oldCapacity) {
					newCapacity = Integer.MAX_VALUE;
				} else {
					newCapacity = oldCapacity << 1;
					if (newCapacity < requiredCapacity) {
						newCapacity = Integer.highestOneBit(requiredCapacity - 1) << 1;
					}
					if (newCapacity < 0) {
						newCapacity = Integer.MAX_VALUE;
					}
				}
				elements = Arrays.copyOf(elements, newCapacity);
			}
			return elements;
		}
	}

	private static final class ArrayCharSequence implements CharSequence {
		private final char[] input;
		private final int begin;
		private final int end;

		ArrayCharSequence(char[] input) {
			this(input, 0, input.length);
		}

		ArrayCharSequence(char[] input, int begin, int end) {
			this.input = input;
			this.begin = begin;
			this.end = end;
		}

		@Override
		public CharSequence subSequence(int begin, int end) {
			return new ArrayCharSequence(
					input,
					checkPositionIndex(this.begin + begin, input.length),
					checkPositionIndex(this.begin + end, input.length));
		}

		@Override
		public int length() {
			return end - begin;
		}

		@Override
		public char charAt(int index) {
			return input[index];
		}

		@Override
		public String toString() {
			return String.valueOf(input, begin, end - begin);
		}
	}

	private static final int INDEX_STEP = 2;

	public static final int EOF = -1;
	public static final int UNRECOGNIZED = -2;
}
