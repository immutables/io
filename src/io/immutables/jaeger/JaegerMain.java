package io.immutables.jaeger;

import com.google.common.io.Files;
import io.immutables.jaeger.parse.ParserFacade;
import java.io.File;
import java.nio.charset.StandardCharsets;

public final class JaegerMain {
	private JaegerMain() {}
	private static final String SOURCE_FILE = "";
	private static final String TARGET_FILE = "";

	public static void main(String... args) throws Exception {
		File sourceFile = new File(SOURCE_FILE);
		File targetFile = new File(TARGET_FILE);

		String source = Files.toString(sourceFile, StandardCharsets.UTF_8);

		ParserFacade parser = new ParserFacade(targetFile.getName(), source);
		if (parser.parse()) {
			Composer composer = new Composer(parser.unit());

		} else {
			System.err.println("ERROR " + parser.message());
		}
	}
}
