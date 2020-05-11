package io.immutables;

public interface Unreachable {
	static AssertionError exhaustive() {
		throw new AssertionError("unreachable code due to exhaustive match");
	}

	static AssertionError wishful() {
		throw new AssertionError("would wish that code be unreachable");
	}

	static AssertionError contractual() {
		throw new AssertionError("code should be unreachable by design contract");
	}

	@SuppressWarnings("unchecked")
	static <X extends Throwable> X uncheckedThrow(Throwable hideous) throws X {
		throw (X) hideous;
	}
}
