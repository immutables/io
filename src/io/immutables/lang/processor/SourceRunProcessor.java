package io.immutables.lang.processor;

import com.google.common.base.Throwables;
import com.google.common.io.CharSource;
import io.immutables.lang.SourceRun;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import org.immutables.generator.AbstractGenerator;
import org.immutables.generator.Generator.SupportedAnnotations;

// TODO once closed to the full compilcation
// need to revisit it
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotations(SourceRun.class)
public final class SourceRunProcessor extends AbstractGenerator {

	@Override
	protected void process() {
		Debug.setConsumer(message ->
				processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, message));

		for (TypeElement fixtureType : ElementFilter.typesIn(round().getElementsAnnotatedWith(SourceRun.class))) {
			PackageElement fixturePackage = processing().getElementUtils().getPackageOf(fixtureType);
			SourceRun sourceFixture = fixtureType.getAnnotation(SourceRun.class);
			String glob = sourceFixture.value();
			try (DirectoryStream<Path> sources =
					Files.newDirectoryStream(packageDir(fixturePackage), glob)) {

				SourceRuns generator = generator();
				generator.packageName = fixturePackage.getQualifiedName().toString();
				generator.fixtureName = fixtureType.getSimpleName().toString();

				for (Path path : sources) {
					String content = new CharSource() {
						@Override
						public Reader openStream() throws IOException {
							return Files.newBufferedReader(path, StandardCharsets.UTF_8);
						}
					}.read();
					
					String filename = path.getFileName().toString();
					String testname = filename.replace('.', '_');
					generator.process(testname, filename, content);
				}

				invoke(generator.generate());

			} catch (Exception ex) {
				throw Throwables.propagate(ex);
			}
		}

		if (Debug.logged()) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "See output");
		}
	}

	private static Path packageDir(PackageElement p) {
		String packagePath = p.isUnnamed() ? "" : ("/" + p.getQualifiedName().toString().replace('.', '/'));
		return Paths.get(System.getProperty("user.dir"), "src" + packagePath);
	}

	private SourceRuns generator() throws Exception {
		Class<SourceRuns> c = SourceRuns.class;
		String generatorClassname = c.getPackage().getName() + ".Generator_" + c.getSimpleName();
		return (SourceRuns) Class.forName(generatorClassname).newInstance();
	}
}
