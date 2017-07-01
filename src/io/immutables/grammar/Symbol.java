package io.immutables.grammar;

import static java.util.Objects.requireNonNull;

public final class Symbol implements CharSequence {
	private final String value;

	private Symbol(String value) {
		this.value = value;
	}

	@Override
	public int length() {
		return value.length();
	}

	@Override
	public char charAt(int index) {
		return value.charAt(index);
	}

	@Override
	public CharSequence subSequence(int begin, int end) {
		return new Symbol(value.substring(begin, end));
	}

	public String unquote() {
		return Escapes.unquote(value);
	}

	@Override
	public String toString() {
		return value;
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ((Symbol) obj).value.equals(value);
	}

	public static Symbol from(String value) {
		return new Symbol(requireNonNull(value));
	}

	public static Symbol from(char[] chars, int begin, int end) {
		return new Symbol(String.valueOf(chars, begin, end - begin));
	}
}