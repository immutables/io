package io.immutables.grammar;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.escape.ArrayBasedCharEscaper;
import com.google.common.escape.Escaper;
import static com.google.common.base.Preconditions.checkArgument;

public final class Escapes {
	private Escapes() {}

	private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

	static char[] escapeUnsafe(char c) {
		char[] result = new char[6];
		result[0] = '\\';
		result[1] = 'u';
		result[5] = HEX_DIGITS[c & 0xF];
		c >>>= 4;
		result[4] = HEX_DIGITS[c & 0xF];
		c >>>= 4;
		result[3] = HEX_DIGITS[c & 0xF];
		c >>>= 4;
		result[2] = HEX_DIGITS[c & 0xF];
		return result;
	}

	private static final ImmutableBiMap<Character, String> ESCAPES =
			ImmutableBiMap.<Character, String>builder()
					.put('\"', "\\\"")
					.put('\'', "\\'")
					.put('\\', "\\\\")
					.put('\b', "\\b")
					.put('\f', "\\f")
					.put('\n', "\\n")
					.put('\r', "\\r")
					.put('\t', "\\t")
					.build();

	private static final ImmutableBiMap<Character, String> ESCAPES_RANGE =
			ImmutableBiMap.<Character, String>builder()
					.putAll(ESCAPES)
					.put(' ', "\\s")
					.put('[', "\\[")
					.put(']', "\\]")
					.put('^', "\\^")
					.put('-', "\\-")
					.build();

	private static final Escaper ESCAPER = new ArrayBasedCharEscaper(
			ESCAPES, ' '/* 0x20 */, '~'/* 0x7E */) {
		@Override
		protected char[] escapeUnsafe(char c) {
			return escapeUnsafe(c);
		}
	};

	private static final Escaper ESCAPER_RANGE = new ArrayBasedCharEscaper(
			ESCAPES_RANGE, ' '/* 0x20 */, '~'/* 0x7E */) {
		@Override
		protected char[] escapeUnsafe(char c) {
			return escapeUnsafe(c);
		}
	};

	public static ImmutableMap<String, Character> unescapesRange() {
		return ESCAPES_RANGE.inverse();
	}

	public static Escaper escaper() {
		return ESCAPER;
	}

	public static Escaper escaperRange() {
		return ESCAPER_RANGE;
	}
	
	public static String angleQuote(String string) {
		return "<" + escaper().escape(string) + ">";
	}

	public static String singleQuote(String string) {
		return "'" + escaper().escape(string) + "'";
	}

	public static String doubleQuote(String string) {
		return "\"" + escaper().escape(string) + "\"";
	}

	public static String unquote(String string) {
		checkArgument(string.length() >= 2, "should be at least 2 chars wide for quotes");
		char first = string.charAt(0);
		char last = string.charAt(string.length() - 1);
		checkArgument(((first == '"'
				|| first == '\''
				|| first == '`')
				&& first == last)
				|| (first == '<' && last == '>'),
				"First and last chars should both be single or double quotes or backticks");

		return string.substring(1, string.length() - 1);
	}
}
