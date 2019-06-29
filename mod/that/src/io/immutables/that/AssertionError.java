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
		StackTraceElement[] trimmedStack = trimStack(getStackTrace());
		this.sourceLine = findSourceLine(trimmedStack);
		if (!fullStackTrace) {
			this.setStackTrace(trimmedStack);
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
		return !sourceLine.isEmpty() ? Diff.markPointer + sourceLine.trim() : "";
	}

	/**
	 * The idea here is that we chop this package's classes from the top, then we chop out all test
	 * framework entry calls including corresponding reflection calls.
	 */
	private static StackTraceElement[] trimStack(StackTraceElement[] stack) {
		int start = 0;
		int end = stack.length;
		int reflective = -1;

		for (int i = 0; i < stack.length; i++) {
			StackTraceElement s = stack[i];
			if (isThatPackageFrame(s)) {
				if (start == i) start = i + 1;
				reflective = -1;
				continue;
			}
			if (isReflectiveFrame(s)) {
				if (reflective == -1) {
					reflective = i;
				}
				continue;
			}
			if (isTestFrameworkFrame(s)) {
				end = reflective != -1 ? reflective : i;
				break;
			}
			reflective = -1;
		}

		return Arrays.asList(stack)
				.subList(start, end)
				.toArray(new StackTraceElement[] {});
	}

	private static boolean isReflectiveFrame(StackTraceElement s) {
		return s.getClassName().startsWith("java.lang.reflect.")
				|| s.getClassName().startsWith("sun.reflect.");
	}

	private static boolean isThatPackageFrame(StackTraceElement s) {
		return s.getClassName().startsWith(thatPackagePrefix)
				&& !s.getClassName().contains(TEST_SUFFIX_OR_PREFIX);
	}

	private static boolean isTestFrameworkFrame(StackTraceElement e) {
		return e.getClassName().startsWith(testFrameworkTypesPrefix);
	}

	private static String findSourceLine(StackTraceElement[] stack) {
		String foundLine = "";
		for (StackTraceElement e : stack) {
			// package declaration or synthetic methods are of no interest
			if (e.getLineNumber() > 0) {
				foundLine = readLine(toResourceName(e), e.getLineNumber());
				if (foundLine.isEmpty())
					continue;
				// This is likely what we want, but will fallback to
				// last matching line (or default empty) otherwise
				if (foundLine.contains("that(")) return foundLine;
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
		if (stream == null) return "";
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

	private static final String thatPackagePrefix = AssertionError.class.getPackage().getName() + ".";

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

	/**
	 * As we manipulate stack trace frames we use our test runner framework name. Rather than guessing
	 * many of them, we just assum junit, be we can override that.
	 */
	private static final String testFrameworkTypesPrefix =
			System.getProperty("io.immutables.that.test-framework-types-prefix", "org.junit.");
}
