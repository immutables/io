package io.immutables.lang.type;

public final class Name implements CharSequence {
	private final String string;

	private Name(String string) {
		this.string = string;
	}

	public static Name empty() {
		return EMPTY;
	}

	public static Name of(CharSequence name) {
		if (name.length() == 0) return EMPTY;
		return new Name(name.toString());
	}

	@Override
	public Name subSequence(int begin, int end) {
		return of(string.substring(begin, end));
	}

	public boolean isEmpty() {
		return this == EMPTY;
	}

	@Override
	public String toString() {
		return string;
	}

	@Override
	public int length() {
		return string.length();
	}

	@Override
	public char charAt(int index) {
		return string.charAt(index);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Name
				? ((Name) other).string.equals(string)
				: false;
	}

	@Override
	public int hashCode() {
		return string.hashCode();
	}

	private static final Name EMPTY = new Name("");
}
