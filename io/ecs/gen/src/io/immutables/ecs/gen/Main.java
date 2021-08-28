package io.immutables.ecs.gen;

import io.immutables.collect.Vect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.immutables.generator.Templates;

public class Main {
  static class Mode {
    boolean schema;
    boolean typescript;
  }

  public static void main(String... args) throws IOException {
    var srcBuilder = Vect.<Path>builder();

    var output = new Output();
    var mode = new Mode();

    initArgs(output, mode, srcBuilder, args);

    Vect<Path> srcs = srcBuilder.build();
    if (srcs.isEmpty()) {
      System.err.println("No sources to compile");
    } else {
      var compiler = new Compiler();
      compiler.addPredef();
      srcs.map(Compiler.Src::from).forEach(compiler::add);

      if (compiler.compile()) {
        var model = compiler.extractModel();
        if (!compiler.problems.isEmpty()) {
          exitWithProblems(compiler);
        }
        if (mode.typescript) {
          var template = new Generator_Typescript();
          template.model = model;
          template.output = output;
          template.generate().invoke(Templates.Invokation.initial());
        } else if (mode.schema) {
					var template = new Generator_Schema();
					template.model = model;
					template.output = output;
					template.generate().invoke(Templates.Invokation.initial());
				} else {
					var template = new Generator_Jawa();
					template.model = model;
					template.output = output;
					template.generate().invoke(Templates.Invokation.initial());
				}
      } else {
        exitWithProblems(compiler);
      }
    }

    output.finalizeResources();
  }

  public static void exitWithProblems(Compiler compiler) {
    for (var o : compiler.problems) {
      System.err.println(o);
    }
    System.exit(2);
  }

  private static void initArgs(Output output, Mode mode, Vect.Builder<Path> srcs, String[] args) {
    var deque = new ArrayDeque<>(Arrays.asList(args));
    while (!deque.isEmpty()) {
      switch (deque.peek()) { // @formatter:off
      case "--typescript": mode.typescript = true; deque.remove(); break;
      case "--schema": mode.schema = true; deque.remove(); break;
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
    System.err.println(
        "Usage: <thiscmd> [--schema|--typescript] [--out <dir>] [--zip <file>] <source_file1> [<source_file1>...]");
    System.exit(-1);
  }
}
