package io.immutables.grammar;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import io.immutables.Unreachable;
import java.util.Arrays;
import java.util.List;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;

@Enclosing
public interface Source {
	@Immutable
	abstract class Range {
		public abstract Position begin();

		@Default
		public Position end() {
			return begin();
		}

		@Check
		void check() {
			checkState(end().position() >= begin().position(), "end position cannot be before begin position");
		}

		public abstract Range withBegin(Position position);

		public abstract Range withEnd(Position position);

		public CharSequence get() {
			return source().length() == 0
					? source()
					: source().subSequence(
							begin().position(),
							end().position());
		}

		@Default
		CharSequence source() {
			return "";
		}

		public CharSequence highlight() {
			return new Source.Excerpt(source()).get(this);
		}

		@Override
		public String toString() {
			if (begin().equals(end())) {
				return "[" + begin() + ")";
			}
			return "[" + begin() + "\u2025" + end() + ")";
		}

		static class Builder extends ImmutableSource.Range.Builder {}
	}

	@Immutable
	abstract class Position {
		/** @return 0-based position. */
		@Parameter
		public abstract int position();
		/** @return 1-based line number. */
		@Parameter
		public abstract int line();
		/** @return 1-based column number. */
		@Parameter
		public abstract int column();

		@Override
		public String toString() {
			return line() + ":" + column();
		}

		@Check
		void check() {
			checkState(position() >= 0, "position is [0..): %s", this);
			checkState(line() >= 1, "line is [1..): %s", this);
			checkState(column() >= 1, "column is [1..) %s", this);
		}

		public static Position of(int position, int line, int column) {
			return ImmutableSource.Position.of(position, line, column);
		}
	}

	static final class Excerpt {
		private final List<String> lines;
		private final int gutterWidth;

		public Excerpt(CharSequence source) {
			this.lines = Splitter.on('\n').splitToList(source);
			this.gutterWidth = Math.max(1, computeLineNumberMagnitude(lines.size()));
		}

		private static int computeLineNumberMagnitude(int size) {
			for (int m = 10, i = 1; m < Integer.MAX_VALUE; m *= 10, i++) {
				if (size / m == 0) return i;
			}
			throw Unreachable.wishful();
		}

		public StringBuilder get(Range range) {
			int lineNumber = range.begin().line();
			int columnStart = range.begin().column();

			int columnEnd;
			if (range.end().line() == lineNumber
					&& range.end().column() != columnStart) {
				columnEnd = range.end().column();
			} else {
				columnEnd = columnStart + 1;
			}

			StringBuilder sb = new StringBuilder();

			int lineIndex = lineNumber - 1;

			appendLinesAbove(sb, lineIndex);
			appendCurrentLine(sb, lineIndex);
			appendSquiggles(columnStart, columnEnd, sb);
			appendLinesBelow(sb, lineIndex);

			return sb;
		}

		private void appendCurrentLine(StringBuilder sb, int lineIndex) {
			gutter(sb, lineIndex).append(lines.get(lineIndex)).append('\n');
		}

		private StringBuilder gutter(StringBuilder sb, int lineIndex) {
			return gutter(sb, String.valueOf(lineIndex + 1));
		}

		private StringBuilder gutter(StringBuilder sb, String source) {
			return sb.append(Strings.padStart(source, gutterWidth, ' ')).append(" |");
		}

		private StringBuilder gutterFill(StringBuilder sb, char c) {
			return sb.append(Strings.repeat(c + "", gutterWidth)).append(" |");
		}

		private void appendSquiggles(int columnStart, int columnEnd, StringBuilder sb) {
			gutterFill(sb, '^');
			int charCount = columnEnd > 0 ? Math.abs(columnEnd - columnStart) : 1;
			for (int i = 0; i < columnStart - 1; i++) {
				sb.append(' ');
			}
			for (int i = 0; i < charCount; i++) {
				sb.append('^');
			}
			sb.append('\n');
		}

		private void appendLinesBelow(StringBuilder sb, int line) {
			if (line + 1 < lines.size()) {
				gutter(sb, line + 1).append(lines.get(line + 1)).append('\n');

				if (line + 2 == lines.size() - 1) {
					gutter(sb, line + 2).append(lines.get(line + 2)).append('\n');
				} else if (line + 2 < lines.size() - 1) {
					// sb.append('\n').append("..");
					gutter(sb, line + 2).append("...").append('\n');
				}
			}
		}

		private void appendLinesAbove(StringBuilder sb, int line) {
			if (line - 1 >= 0) {
				if (line - 2 == 0) {
					gutter(sb, line - 2).append(lines.get(line - 2)).append('\n');
				} else if (line - 2 > 0) {
					// sb.append('\n').append("..");
					gutter(sb, line - 2).append("...").append('\n');
				}
				gutter(sb, line - 1).append(lines.get(line - 1)).append('\n');
			}
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int line = 0; line < lines.size(); line++) {
				gutter(sb, line).append(lines.get(line));
			}
			return sb.toString();
		}
	}

	final class Lines {
		private int[] lines;
		private int count;

		private Lines(int[] lines, int count) {
			this.lines = lines;
			this.count = count;
		}

		public int count() {
			return count;
		}

		public Range getLine(CharSequence source, int lineNumber) {
			checkPositionIndex(lineNumber - 1, count);
			int before = lines[lineNumber - 1];
			int after = lines[lineNumber];
			if (after == 0) {
				after = source.length();
			}
			return new Range.Builder()
					.source(source)
					.begin(Position.of(before + 1, lineNumber, 1))
					.end(Position.of(after, lineNumber, after - before))
					.build();
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
			Tracker t = new Tracker();
			for (int i = 0; i < input.length; i++) {
				if (input[i] == '\n') {
					t.addNewlineAt(i);
				}
			}
			return t.lines();
		}

		public static class Tracker {
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

			public Lines lines() {
				return new Lines(lines, count);
			}
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + count + "]";
		}
	}

	static CharSequence wrap(char[] input) {
		class Wrapper implements CharSequence {
			private final char[] input;
			private final int begin;
			private final int end;

			Wrapper(char[] input) {
				this(input, 0, input.length);
			}

			Wrapper(char[] input, int begin, int end) {
				this.input = input;
				this.begin = begin;
				this.end = end;
			}

			@Override
			public CharSequence subSequence(int begin, int end) {
				int newBegin = checkPositionIndex(this.begin + begin, input.length);
				int newEnd = checkPositionIndex(this.begin + end, input.length);
				return new Wrapper(input, newBegin, newEnd);
			}

			@Override
			public int length() {
				return end - begin;
			}

			@Override
			public char charAt(int index) {
				return input[begin + index];
			}

			@Override
			public String toString() {
				return String.valueOf(input, begin, end - begin);
			}
		}

		return new Wrapper(input);
	}
}
