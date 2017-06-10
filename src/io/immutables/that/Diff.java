package io.immutables.that;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Note: Inspired by JUnit's implementation
 * Original note: Inspired by a patch from Alex Chaffee (alex@purpletech.com)
 * @author JUnit authors
 * @author Alex Chaffee (alex@purpletech.com)
 * @author Ievgen Lukash
 */
final class Diff {
	private static final String ELLIPSIS = "...";
	private static final String DIFF_END = "]";
	private static final String DIFF_START = "[";
	private static final int COMPACTION_LENGTH_THRESHOLD = 40;

	/**
	 * The maximum length for {@link #expected} and {@link #actual} strings to show. When
	 * {@link #compactionLength} is exceeded, the Strings are shortened.
	 */
	private final int compactionLength;
	private final String expected;
	private final String actual;

	/**
	 * @param compactionLength the maximum length of context surrounding the difference
	 *          between the
	 *          compared strings. When context length is exceeded, the prefixes and suffixes are
	 *          compacted.
	 * @param expected the expected string value
	 * @param actual the actual string value
	 */
	Diff(int compactionLength, String expected, String actual) {
		this.compactionLength = compactionLength;
		this.expected = expected;
		this.actual = actual;
	}

	static List<String> diff(@Nullable Object expected, @Nullable Object actual) {
		return new Diff(
				COMPACTION_LENGTH_THRESHOLD,
				Objects.toString(expected),
				Objects.toString(actual)).asList();
	}

	static String trim(@Nullable Object actual) {
		return diff(actual, actual).get(0);
	}

	List<String> asList() {
		String prefix = prefix();
		String suffix = suffix(prefix);

		if (prefix.isEmpty() && suffix.isEmpty()) {
			return Arrays.asList(expected, actual);
		}

		String compactedPrefix = compactPrefix(prefix);
		String compactedSuffix = compactSuffix(suffix);

		return Arrays.asList(
				compactedPrefix + diff(prefix, suffix, expected) + compactedSuffix,
				compactedPrefix + diff(prefix, suffix, actual) + compactedSuffix);
	}

	private String compactPrefix(String prefix) {
		if (prefix.length() <= compactionLength) {
			return prefix;
		}
		return ELLIPSIS + prefix.substring(prefix.length() - compactionLength);
	}

	private String compactSuffix(String suffix) {
		if (suffix.length() <= compactionLength) {
			return suffix;
		}
		return suffix.substring(0, compactionLength) + ELLIPSIS;
	}

	private static String diff(String prefix, String suffix, String text) {
		return DIFF_START + text.substring(prefix.length(), text.length() - suffix.length()) + DIFF_END;
	}

	private String prefix() {
		int length = Math.min(expected.length(), actual.length());
		for (int i = 0; i < length; i++) {
			if (expected.charAt(i) != actual.charAt(i)) {
				return expected.substring(0, i);
			}
		}
		return expected.substring(0, length);
	}

	private String suffix(String prefix) {
		int suffixLength = 0;
		int maxSuffixLength = Math.min(
				expected.length() - prefix.length(),
				actual.length() - prefix.length()) - 1;

		for (; suffixLength <= maxSuffixLength; suffixLength++) {
			if (expected.charAt(expected.length() - 1 - suffixLength)
				!= actual.charAt(actual.length() - 1 - suffixLength)) {
				break;
			}
		}
		return expected.substring(expected.length() - suffixLength);
	}

	@Override
	public String toString() {
		return asList().toString();
	}
}