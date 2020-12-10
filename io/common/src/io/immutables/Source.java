package io.immutables;

import com.google.common.base.Strings;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.Arrays;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkPositionIndexes;
import static com.google.common.base.Preconditions.checkState;

public interface Source {

	final class Position {
		/** 0-based position. */
		public final int position;
		/** 1-based line number. */
		public final int line;
		/** 1-based column number. */
		public final int column;

		private Position(int position, int line, int column) {
			this.position = position;
			this.line = line;
			this.column = column;
			checkState(position >= 0, "position is [0..): %s", position);
			checkState(line >= 1, "line is [1..): %s", line);
			checkState(column >= 1, "column is [1..) %s", column);
		}

		public static Position of(int position, int line, int column) {
			return new Position(position, line, column);
		}

		@Override
		public String toString() {
			return line + ":" + column;
		}

		@Override
		public int hashCode() {
			int h = 5381;
			h += (h << 5) + position;
			h += (h << 5) + line;
			h += (h << 5) + column;
			return h;
		}

		@Override
		public boolean equals(@Nullable Object another) {
			if (!(another instanceof Position)) return false;
			Position obj = (Position) another;
			return position == obj.position
					&& line == obj.line
					&& column == obj.column;
		}
	}

	final class Range {
		public final Position begin;
		public final Position end;

		private Range(Position begin, Position end) {
			checkState(begin.position <= end.position, "end position cannot be before begin position");
			this.begin = begin;
			this.end = end;
		}

		public static Range of(Position begin, Position end) {
			return new Range(begin, end);
		}

		public static Range of(Position begin) {
			return of(begin, begin);
		}

		public CharSequence get(CharSequence source) {
			return source.subSequence(begin.position, end.position);
		}
		
		/** Either this range if within line or only starting position range. */
		public Range withinLine() {
			if (begin.line == end.line) return this;
			return new Range(begin, begin);
		}

		public Range span(Range to) {
			return Range.of(this.begin, to.end);
		}

		@Override
		public String toString() {
			return begin.equals(end)
					? "[" + begin + ")"
					: "[" + begin + "\u2025" + end + ")";
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Range)) return false;
			Range r = (Range) obj;
			return begin.equals(r.begin)
					&& end.equals(r.end);
		}

		@Override
		public int hashCode() {
			int h = 5381;
			h += (h << 5) + begin.hashCode();
			h += (h << 5) + end.hashCode();
			return h;
		}
	}

	final class Excerpt {
		private final int gutterWidth;
		private final CharSequence source;
		private final Lines lines;

		private Excerpt(CharSequence source, Lines lines) {
			this.source = source;
			this.lines = lines;
			this.gutterWidth = Math.max(1, computeLineNumberMagnitude(lines.count()));
		}

		public static Excerpt from(CharSequence source) {
			return new Excerpt(source, Lines.from(source));
		}

		public static Excerpt from(CharSequence source, Lines lines) {
			return new Excerpt(source, lines);
		}

		private static int computeLineNumberMagnitude(int size) {
			for (int m = 10, i = 1; m < Integer.MAX_VALUE; m *= 10, i++) {
				if (size / m == 0) return i;
			}
			throw Unreachable.wishful();
		}

		private CharSequence getLine(int lineNumber) {
			return lines.getLineRange(lineNumber).get(source);
		}

		public StringBuilder get(Range range) {
			int lineNumber = range.begin.line;
			int columnStart = range.begin.column;

			int columnEnd;
			if (range.end.line == lineNumber
					&& range.end.column != columnStart) {
				columnEnd = range.end.column;
			} else {
				columnEnd = columnStart + 1;
			}

			StringBuilder sb = new StringBuilder();

			appendLinesAbove(sb, lineNumber);
			appendLine(sb, lineNumber);
			appendSquiggles(sb, lineNumber, columnStart, columnEnd);
			appendLinesBelow(sb, lineNumber);

			return sb;
		}

		private void appendSquiggles(StringBuilder sb, int lineNumber, int columnStart, int columnEnd) {
			gutterFill(sb, '^');

			CharSequence line = getLine(lineNumber);
			int squiggleStart = columnStart - 1;
			int squiggleWidth = Math.max(columnEnd - columnStart, 1);

			for (int i = 0; i < squiggleStart; i++) {
				if (line.charAt(i) == '\t') {
					appendRepeat(sb, ' ', TAB_WIDTH);
				} else {
					sb.append(' ');
				}
			}

			for (int i = squiggleStart; i < squiggleStart + squiggleWidth; i++) {
				if (i < line.length() && line.charAt(i) == '\t') {
					appendRepeat(sb, '^', TAB_WIDTH);
				} else {
					sb.append('^');
				}
			}

			sb.append('\n');
		}

		private void appendLinesAbove(StringBuilder sb, int lineNumber) {
			if (lineNumber > 1) {
				if (lineNumber == 3) {
					appendLine(sb, lineNumber - 2);
				} else if (lineNumber > 3) {
					appendLine(sb, lineNumber - 2, ELLIPSIS);
				}
				appendLine(sb, lineNumber - 1);
			}
		}

		private void appendLinesBelow(StringBuilder sb, int lineNumber) {
			if (lineNumber + 1 <= lines.count()) {
				appendLine(sb, lineNumber + 1);
				if (lineNumber + 2 == lines.count()) {
					appendLine(sb, lineNumber + 2);
				} else if (lineNumber + 2 < lines.count()) {
					appendLine(sb, lineNumber + 2, ELLIPSIS);
				}
			}
		}

		private void appendLine(StringBuilder sb, int lineNumber) {
			appendLine(sb, lineNumber, getLine(lineNumber));
		}

		private void appendLine(StringBuilder sb, int lineNumber, CharSequence content) {
			gutter(sb, String.valueOf(lineNumber));
			for (int i = 0; i < content.length(); i++) {
				char c = content.charAt(i);
				if (c == '\t') {
					appendRepeat(sb, ' ', TAB_WIDTH);
				} else {
					sb.append(c);
				}
			}
			sb.append('\n');
		}

		private StringBuilder gutter(StringBuilder sb, String gutter) {
			return sb.append(Strings.padStart(gutter, gutterWidth, ' ')).append(GUTTER_SEPARATOR);
		}

		private StringBuilder gutterFill(StringBuilder sb, char c) {
			return appendRepeat(sb, c, gutterWidth).append(GUTTER_SEPARATOR);
		}

		private StringBuilder appendRepeat(StringBuilder sb, char c, int times) {
			for (int i = 0; i < times; i++) {
				sb.append(c);
			}
			return sb;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int l = 1; l <= lines.count(); l++) {
				appendLine(sb, l);
			}
			return sb.toString();
		}

		private static final String ELLIPSIS = "\u2026";
		private static final String GUTTER_SEPARATOR = " |";
		private static final int TAB_WIDTH = Integer.getInteger("im.source.tab-width", 2);
	}

	final class Problem {
		public final String filename;
		public final CharSequence source;
		public final Range range;
		public final String message;
		public final String hint;
		public final Lines lines;

		public Problem(String filename, CharSequence source, Lines lines, Range range, String message, String hint) {
			this.filename = filename;
			this.source = source;
			this.lines = lines;
			this.range = range;
			this.message = message;
			this.hint = hint;
		}

		@Override
		public String toString() {
			return filename + ":" + range.begin + " " + message + "\n\t"
					+ Source.Excerpt.from(source, lines).get(range).toString().replace("\n", "\n\t")
					+ hint + "\n";
		}
	}

	final class Lines {
		private final int[] lines;
		private final int count;

		private Lines(int[] lines, int count) {
			this.lines = lines;
			this.count = count;
		}

		public int count() {
			return count;
		}

		public Range getLineRange(int lineNumber) {
			checkPositionIndex(lineNumber - 1, count);
			int before = lines[lineNumber - 1];
			int after = lines[lineNumber];
			return Range.of(
					Position.of(before + 1, lineNumber, 1),
					Position.of(after, lineNumber, after - before));
		}

		public Position get(int position) {
			checkArgument(position >= 0, "position >= 0");
			int lineIndex = Arrays.binarySearch(lines, 0, count, position);
			if (lineIndex < 0) {
				lineIndex = -lineIndex - 2;
			}
			int lineNumber = lineIndex + 1;
			int columnNumber = position - lines[lineIndex];
			// we've got at newline
			if (columnNumber == 0) {
				columnNumber = position - lines[lineIndex - 1];
				lineNumber--;
			}
			return Position.of(position, lineNumber, columnNumber);
		}

		public static Lines from(char[] input) {
			return from(wrap(input));
		}

		public static Lines from(CharSequence input) {
			Tracker t = new Tracker();
			int length = input.length();
			for (int i = 0; i < length; i++) {
				if (input.charAt(i) == '\n') {
					t.addNewlineAt(i);
				}
			}
			return t.lines(length);
		}

		public static final class Tracker {
			private int[] lines = new int[128];
			{
				// last position before first line is -1
				// i.e. non-existing zero line ends on -1 position, on the next, 0
				// position first line starts.
				lines[0] = -1;
			}
			// at least one line is available starting from 0 position, even if
			// input is empty
			private int count = 1;

			public int addNewlineAt(int position) {
				// recording end of the line
				lines = Capacity.ensure(lines, count, 1);
				lines[count++] = position;
				return count;
			}

			public Lines lines(int length) {
				// mark next position after last line,
				// which is next to last position - equal to length
				lines[count] = length;
				return new Lines(lines, count);
			}
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + count + "]";
		}
	}

	/**
	 * Clean implementation of in memory CharSequence and Writer/Appendable.
	 * Auto-grows akin to ArrayList. Contains common optimization for character copying.
	 * Create shallow subsequences and otherwise minimizes copying/object creation.
	 */
	public final class Buffer extends Writer implements CharSequence {
		private static final int MIN_CAPACITY = 128;
		private char[] data = new char[0];
		private int limit = 0;

		public Buffer() {
			this(MIN_CAPACITY);
		}

		public Buffer(int initialCapacity) {
			data = Capacity.ensure(data, limit, initialCapacity);
		}

		@Override
		public int length() {
			return limit;
		}

		@Override
		public char charAt(int index) {
			checkPositionIndex(index, limit);
			return data[index];
		}

		/**
		 * We do shallow copy subsequence, whereas StringBuilder does copy character content.
		 * They did it do minimize accidental memory leaks. Our classes are very specialized
		 * and are to be used carefully, so it's beneficial to share underlying char data
		 * and copy explicitly when needed: {@code wrap(buffer.toCharArray())}
		 */
		@Override
		public CharSequence subSequence(int begin, int end) {
			checkPositionIndexes(begin, end, limit);
			return new SourceWrapper(data, begin, end);
		}

		@Override
		public String toString() {
			return String.valueOf(data, 0, limit);
		}

		@Override
		public void flush() {}

		@Override
		public void close() {}

		@Override
		public Buffer append(CharSequence cs) {
			if (cs instanceof CharBuffer) {
				// this optimization is only applicable without start/end ranges,
				// otherwise we would include it in applend(cs, start, end) implementation
				int increment = cs.length();
				data = Capacity.ensure(data, limit, increment);
				((CharBuffer) cs).get(data, limit, increment);
				limit += increment;
				return this;
			}
			return append(cs, 0, cs.length());
		}

		@Override
		public Buffer append(CharSequence cs, int start, int end) {
			int increment = end - start;
			data = Capacity.ensure(data, limit, increment);
			// trying to optimize char[] data copying for common
			// known CharSequence implementation, otherwise resort
			// to per-character copying, which is obviously slower
			// for practical file or stream reading.
			if (cs instanceof String) {
				((String) cs).getChars(start, end, data, limit);
			} else if (cs instanceof StringBuilder) {
				((StringBuilder) cs).getChars(start, end, data, limit);
			} else {
				for (int i = 0; i < increment; i++) {
					data[limit + i] = cs.charAt(start + i);
				}
			}
			limit += increment;
			return this;
		}

		@Override
		public void write(String string) {
			write(string, 0, string.length());
		}

		@Override
		public void write(String string, int offset, int length) {
			data = Capacity.ensure(data, limit, length);
			string.getChars(offset, offset + length, data, limit);
			limit += length;
		}

		@Override
		public void write(char[] buffer) {
			write(buffer, 0, buffer.length);
		}

		@Override
		public void write(char[] buffer, int offset, int length) {
			data = Capacity.ensure(data, limit, length);
			System.arraycopy(buffer, offset, data, limit, length);
			limit += length;
		}

		@Override
		public void write(int c) {
			append((char) c);
		}

		@Override
		public Buffer append(char c) {
			data = Capacity.ensure(data, limit, MIN_CAPACITY);
			data[limit++] = c;
			return this;
		}

		/** String and StringBuilder -like character copy to destination array. */
		public void getChars(int srcStart, int srcEnd, char[] dest, int destStart) {
			checkPositionIndexes(srcStart, srcEnd, limit);
			System.arraycopy(data, srcStart, dest, destStart, srcEnd - srcStart);
		}

		/** Provides unsafe access to the raw underlying array. */
		public char[] array() {
			return data;
		}

		/**
		 * Resets internal content size to 0 and leaving internal character array
		 * intact, effectively "forgetting" any content written so far. Could be use
		 * for repeatable reading into the same array.
		 */
		public void reset() {
			limit = 0;
		}

		/** Safe copy of the internal data trimmed to the actual content length. */
		public char[] toCharArray() {
			return Arrays.copyOf(data, limit);
		}
	}

	final class SourceWrapper implements CharSequence {
		private final char[] data;
		private final int begin;
		private final int end;

		SourceWrapper(char[] data) {
			this(data, 0, data.length);
		}

		SourceWrapper(char[] data, int begin, int end) {
			this.data = data;
			this.begin = begin;
			this.end = end;
		}

		@Override
		public CharSequence subSequence(int begin, int end) {
			checkPositionIndexes(begin, end, length());
			return new SourceWrapper(data, this.begin + begin, this.begin + end);
		}

		@Override
		public int length() {
			return end - begin;
		}

		@Override
		public char charAt(int index) {
			checkPositionIndex(index, end - begin);
			return data[begin + index];
		}

		@Override
		public String toString() {
			return String.valueOf(data, begin, end - begin);
		}
	}

	static CharSequence wrap(char[] input) {
		return new SourceWrapper(input);
	}
}
