package io.immutables.grammar;

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
	public Symbol subSequence(int begin, int end) {
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

	public boolean isEmpty() {
		return value.isEmpty();
	}

	@Override
	public boolean equals(Object obj) {
		return ((Symbol) obj).value.equals(value);
	}

	public static Symbol from(CharSequence value) {
		return new Symbol(value.toString());
	}

	public static Symbol from(char[] chars, int begin, int end) {
		return new Symbol(String.valueOf(chars, begin, end - begin));
	}
}
