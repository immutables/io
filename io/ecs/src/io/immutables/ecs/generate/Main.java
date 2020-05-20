package io.immutables.ecs.generate;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.immutables.generator.Templates;

public class Main {
	public static void main(String... args) {
		var template = new Generator_Ecs();
		initArgs(template.output, args);
		template.generate().invoke(Templates.Invokation.initial());
		template.output.finalizeResources();
	}

	private static void initArgs(Output output, String... args) {
		var deque = new ArrayDeque<>(Arrays.asList(args));
		while (!deque.isEmpty()) {
			switch (deque.peek()) { // @formatter:off
			case "--out": output.out = requireValue(deque); break;
			case "--zip": output.zip = requireValue(deque); break;
			default:  // @formatter:on
				System.err.println("Unsupported argument '" + deque.peek() + "'");
				exitWithUsage();
			}
		}
	}

	private static String requireValue(Deque<String> deque) {
		assert !deque.isEmpty();
		String opt = deque.remove();
		if (deque.isEmpty() || deque.peek().startsWith("--")) {
			System.err.println("Missing value for '" + opt + "'");
			exitWithUsage();
		}
		return deque.remove();
	}

	private static void exitWithUsage() {
		System.err.println("Usage: <thiscmd> [--out <dir>][--zip <file>]");
		System.exit(-1);
	}
}
