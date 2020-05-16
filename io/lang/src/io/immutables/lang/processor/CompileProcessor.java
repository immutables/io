package io.immutables.lang.processor;

import io.immutables.lang.Compile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import org.immutables.generator.AbstractGenerator;
import org.immutables.generator.Generator.SupportedAnnotations;

/** Compiler embedded as annotation processor. */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedAnnotations(Compile.class)
public final class CompileProcessor extends AbstractGenerator {

	@Override
	protected void process() {
		Debug.setConsumer(message -> processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, message));

		for (PackageElement compileOriginType : ElementFilter.packagesIn(round().getElementsAnnotatedWith(Compile.class))) {
			PackageElement fixturePackage = processing().getElementUtils().getPackageOf(compileOriginType);
			Compile compile = compileOriginType.getAnnotation(Compile.class);
			String glob = compile.value();

			try (DirectoryStream<Path> sources =
					Files.newDirectoryStream(packageDir(fixturePackage), glob)) {

				for (Path path : sources) {
					String content = Files.readString(path, StandardCharsets.UTF_8);
					String filename = path.getFileName().toString();
					processing().getFiler();
				}
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		if (Debug.logged()) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "See output");
		}
	}

	private Path packageDir(PackageElement p) {
		String resourcesRoot = processingEnv.getOptions().getOrDefault("resources.root", ".");
		String packagePath = p.isUnnamed() ? "" : ("/" + p.getQualifiedName().toString().replace('.', '/'));
		return Paths.get(resourcesRoot, packagePath);
	}
}
