package io.immutables.that;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Special extension of assertion error to provide convenience formatting of mismatch information
 * and stack trace.
 */
final class AssertionError extends java.lang.AssertionError {

	private final boolean isBuck;
	private final String assertion;

	AssertionError(String... mismatch) {
		super(join(mismatch));
		StackTraceElement[] stack = getStackTrace();
		this.isBuck = hasBuckClasses(stack);
		this.assertion = extractAssertion(stack);
		this.setStackTrace(trimStack(stack));
	}

	private static String join(String... mismatch) {
		return Stream.of(mismatch).collect(Collectors.joining("\n\t", "\n\t", ""));
	}

	@Override
	public String getMessage() {
		return isBuck && !assertion.isEmpty() ? assertion : super.getMessage();
	}

	@Override
	public String toString() {
		if (isBuck && !assertion.isEmpty()) {
			return getClass().getCanonicalName() + ":" + "  ^^^^^^^^^^^^^^^^^^^^^^" + super.getMessage();
		}
		return super.toString();
	}

	private boolean hasBuckClasses(StackTraceElement[] stack) {
		return Stream.of(stack).anyMatch(e -> e.getClassName().startsWith("com.facebook.buck"));
	}

	private static StackTraceElement[] trimStack(StackTraceElement[] stack) {
		/* Here we trimming anything including and above current package ("that").
		 * And everything excluding below currentlty failed test class (like JUnit runners)
		 */
		int start = 0;
		for (int i = stack.length - 1; i >= 0; i--) {
			StackTraceElement s = stack[i];
			if (isThatPackageFrame(s)) {
				start = Math.min(i + 1, stack.length - 1);
				break;
			}
		}

		int end = stack.length - 1;
		for (int i = stack.length - 1; i >= 0; i--) {
			StackTraceElement s = stack[i];
			if (isTestClassFrame(s)) {
				end = Math.min(i + 1, stack.length - 1);
				break;
			}
		}

		return Arrays.asList(stack)
				.subList(start, end)
				.toArray(new StackTraceElement[] {});
	}

	private static boolean isThatPackageFrame(StackTraceElement s) {
		return s.getClassName().startsWith(AssertionError.class.getPackage().getName());
	}

	private static boolean isTestClassFrame(StackTraceElement e) {
		return e.getFileName().startsWith("Test");
	}

	static String extractAssertion(StackTraceElement[] stack) {
		for (StackTraceElement e : stack) {
			if (isTestClassFrame(e)) {
				if (e.getLineNumber() > 0) {
					String resourceName = "/" + getPackageName(e.getClassName()).replace('.', '/') + "/" + e.getFileName();
					Optional<String> line = asAssertion(readLine(resourceName, e.getLineNumber()));
					if (line.isPresent()) {
						return line.get();
					}
				}
			}
		}
		return "";
	}

	private static Optional<String> asAssertion(String line) {
		String trimmed = line.trim();
		if (trimmed.contains("that(")) {
			return Optional.of(trimmed);
		}
		return Optional.empty();
	}

	private static String readLine(String resourceName, int lineNumber) {
		assert lineNumber >= 1;

		@Nullable InputStream stream = AssertionError.class.getResourceAsStream(resourceName);
		if (stream == null) return "";

		try (BufferedReader r =
				new BufferedReader(
						new InputStreamReader(stream, StandardCharsets.UTF_8))) {

			return r.lines()
					.skip(lineNumber - 1)
					.findFirst()
					.orElse("");

		} catch (IOException ex) {
			return "";
		}
	}

	private static String getPackageName(String qualifiedName) {
		int lastDot = qualifiedName.lastIndexOf('.');
		return lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
	}
}
