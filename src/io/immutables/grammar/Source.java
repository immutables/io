package io.immutables.grammar;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import io.immutables.Unreachable;
import java.util.List;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
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
		public CharSequence source() {
			return "";
		}

		public CharSequence highlight() {
			return new Source.Excerpt(source()).get(this);
		}

		@Override
		public String toString() {
			return "[" + (begin().equals(end())
					? begin()
					: (begin() + "\u2025" + end())) + ")";
		}

		public static class Builder extends ImmutableSource.Range.Builder {}
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
			checkState(position() >= 0, "position is [0..)");
			checkState(line() >= 1, "line is [1..)");
			checkState(column() >= 1, "column is [1..)");
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
			int columnNumber = range.begin().column();

			lineNumber = Math.max(lineNumber, 1);
			columnNumber = Math.max(columnNumber, 0);

			// This is not so good, need to revise
			int columnEnd = 0;
			if (range.end().line() == lineNumber
					&& range.end().column() != columnNumber) {
				columnEnd = range.end().column();
			}

			StringBuilder sb = new StringBuilder();

			int line = lineNumber - 1;

			if (line - 1 >= 0) {
				if (line - 2 == 0) {
					gutter(sb, line - 2).append(lines.get(line - 2)).append('\n');
				} else if (line - 2 > 0) {
					// sb.append('\n').append("..");
					gutter(sb, line - 2).append("...").append('\n');
				}
				gutter(sb, line - 1).append(lines.get(line - 1)).append('\n');
			}

			gutter(sb, line).append(lines.get(line)).append('\n');

			gutterFill(sb, '^');
			int charCount = columnEnd > 0 ? Math.abs(columnEnd - columnNumber) : 1;
			// As it was in parboiled
			// Math.max(Math.min(endIndex - startIndex, StringUtils.length(lineText) - column + 2), 1);
			for (int i = 0; i < columnNumber - 1; i++) {
				sb.append(' ');
			}
			for (int i = 0; i < charCount; i++) {
				sb.append('^');
			}
			sb.append('\n');

			if (line + 1 < lines.size()) {
				gutter(sb, line + 1).append(lines.get(line + 1)).append('\n');

				if (line + 2 == lines.size() - 1) {
					gutter(sb, line + 2).append(lines.get(line + 2)).append('\n');
				} else if (line + 2 < lines.size() - 1) {
					// sb.append('\n').append("..");
					gutter(sb, line + 2).append("...").append('\n');
				}
			}

			return sb;
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

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int line = 0; line < lines.size(); line++) {
				gutter(sb, line).append(lines.get(line));
			}
			return sb.toString();
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
