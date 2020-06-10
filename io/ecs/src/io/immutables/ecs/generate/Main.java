package io.immutables.ecs.generate;

import io.immutables.collect.*;

import java.nio.file.*;
import java.util.*;
import org.immutables.generator.Templates;

public class Main {
	public static void main(String... args) {
		var template = new Generator_Ecs();
		var srcBuilder = Vect.<Path>builder();
		initArgs(template.output, srcBuilder, args);

		Vect<Path> srcs = srcBuilder.build();
		if (srcs.isEmpty()) {
			System.err.println("No sources to compile");
		} else {
			var compiler = new Compiler();
			srcs.map(Compiler.Src::from).forEach(compiler::add);

			if (compiler.compile()) {
				template.generate().invoke(Templates.Invokation.initial());
			} else {
				for (var o : compiler.problems) {
					System.err.println(o.toString());
				}
				System.exit(2);
			}
		}

		template.output.finalizeResources();
	}

	private static void initArgs(Output output, Vect.Builder<Path> srcs, String[] args) {
		var deque = new ArrayDeque<>(Arrays.asList(args));
		while (!deque.isEmpty()) {
			switch (deque.peek()) { // @formatter:off
			case "--out": output.out = requireValue(deque); break;
			case "--zip": output.zip = requireValue(deque); break;
			default:  // @formatter:on
				if (deque.peek().startsWith("-")) {
					System.err.println("Unsupported option '" + deque.peek() + "'");
					exitWithUsage();
				}
				Path path = Path.of(deque.remove());
				if (!Files.isRegularFile(path)) {
					System.err.println("Not a file: " + path);
					exitWithUsage();
				}
				srcs.add(path);
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
		System.err.println("Usage: <thiscmd> [--out <dir>][--zip <file>] <source_file1> [ <source_file1>...]");
		System.exit(-1);
	}
}
