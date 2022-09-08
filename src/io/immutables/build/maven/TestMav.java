package io.immutables.build.maven;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import io.immutables.build.maven.Pom.Project;
import java.io.File;
import java.io.IOException;
import org.immutables.gson.stream.JsonParserReader;
import org.junit.Test;
import static com.google.gson.internal.bind.TypeAdapters.BOOLEAN_AS_STRING;
import static io.immutables.that.Assert.that;

public class TestMav {
	private static final String SAMPLE_POM = "/Users/Shared/Git/elucash/sndbx/immutables/pom.xml"; // value-fixture/

	@Test
	public void testName() {
		that().is(true);
	}

	public static void main(String... args) throws Exception {

		Gson gson = new GsonBuilder()
				.registerTypeAdapter(boolean.class, BOOLEAN_AS_STRING)
				.registerTypeAdapterFactory(new GsonAdaptersPom())
				.create();

		TypeAdapter<Project> adapter = gson.getAdapter(Project.class);

		Project pom = parse(SAMPLE_POM, adapter);

		System.out.println(pom);
	}

	private static <T> T parse(String file, TypeAdapter<T> adapter) throws IOException {
		try (JsonParser parser = new XmlFactory().createParser(new File(file))) {
			return adapter.read(new JsonParserReader(parser));
		}
	}
}
