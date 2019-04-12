package io.immutables.that;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Special extension of {@link java.lang.AssertionError} to provide convenience formatting of
 * mismatch information and stack trace.
 */
final class AssertionError extends java.lang.AssertionError {
	private final String sourceLine;

	AssertionError(String... mismatch) {
		super(join(mismatch));
		StackTraceElement[] stack = getStackTrace();
		this.sourceLine = findSourceLine(stack);
		if (!fullStackTrace) {
			this.setStackTrace(trimStack(stack));
		}
	}

	private static String join(String... mismatch) {
		return Stream.of(mismatch).collect(Collectors.joining("\n\t", "\n\t", ""));
	}

	@Override
	public String getMessage() {
		return !replaceErrorMessage.isEmpty() ? replaceErrorMessage : super.getMessage();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + showSourceHint() + super.getMessage();
	}

	private String showSourceHint() {
		return !sourceLine.isEmpty() ? "~~~> " + sourceLine.trim() : "";
	}

	private static StackTraceElement[] trimStack(StackTraceElement[] stack) {
		// Here we trimming anything including and above current package ("that").
		// And everything excluding below currentlty failed test class (like JUnit runners)
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
		return e.getFileName().startsWith(TEST_SUFFIX_OR_PREFIX)
				|| e.getFileName().endsWith(TEST_SUFFIX_OR_PREFIX);
	}

	private static String findSourceLine(StackTraceElement[] stack) {
		String foundLine = "";
		for (StackTraceElement e : stack) {
			if (isTestClassFrame(e)) {
				// package declaration or synthetic methods are of no interest
				if (e.getLineNumber() > 0) {
					foundLine = readLine(toResourceName(e), e.getLineNumber());
					if (foundLine.isEmpty())
						continue;
					// This is likely what we want, but will fallback to
					// last matching line (or default empty) otherwise
					if (foundLine.contains("that("))
						return foundLine;
				}
			}
		}
		return foundLine;
	}

	private static String toResourceName(StackTraceElement e) {
		return "/" + getPackagePath(e.getClassName()) + "/" + e.getFileName();
	}

	private static String getPackagePath(String qualifiedName) {
		// in binary-form qualified name, nested classes use '$' separator
		// so we should be ok with last dot
		int lastDot = qualifiedName.lastIndexOf('.');
		return lastDot < 0 ? "" : qualifiedName.substring(0, lastDot).replace('.', '/');
	}

	private static String readLine(String resourceName, int lineNumber) {
		assert lineNumber > 0;

		@Nullable InputStream stream = AssertionError.class.getResourceAsStream(resourceName);
		if (stream == null)
			return "";

		try (
				Reader in = new InputStreamReader(stream, StandardCharsets.UTF_8);
				BufferedReader r = new BufferedReader(in)) {
			return r.lines()
					.skip(lineNumber - 1)
					.findFirst()
					.orElse("");
		} catch (IOException ex) {
			return "";
		}
	}

	private static final String TEST_SUFFIX_OR_PREFIX = "Test";

	/**
	 * Some test reporting systems output both message and toString
	 * if this replacement is enabled, we're outputting it when getMessage
	 * is called. But toString will still output our message
	 */
	private static final String replaceErrorMessage = System.getProperty("io.immutables.that.replace-error-message", "");

	/**
	 * If full stack trace should always be available, with no trimming it for convenient output
	 */
	private static final boolean fullStackTrace = Boolean.getBoolean("io.immutables.that.full-stack-trace");
}
