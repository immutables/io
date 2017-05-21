package io.immutables.jaeger;

import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.immutables.jaeger.parse.ParserFacade;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestJaeger {
	private static final String RESOURCE_NAME = "api.test.jg";
	CharSource source = Resources.asCharSource(TestJaeger.class.getResource(RESOURCE_NAME), StandardCharsets.UTF_8);
	@Test
	public void parse() throws IOException {
		ParserFacade parser = new ParserFacade(RESOURCE_NAME, source.read());
		that(parser.parse()).orFail(parser.message().toString());
	}

	@Test
	public void process() throws IOException {
		ParserFacade parser = new ParserFacade(RESOURCE_NAME, source.read());
		that(parser.parse()).orFail(parser.message().toString());
		Composer composer = new Composer(parser.unit());
		composer.process();

		String yaml = Confs.toYaml(composer.compose());
		System.out.println(yaml);
	}
}
