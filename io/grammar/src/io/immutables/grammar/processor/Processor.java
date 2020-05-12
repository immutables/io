package io.immutables.grammar.processor;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import io.immutables.collect.Vect;
import io.immutables.grammar.Grammar;
import io.immutables.grammar.processor.Grammars.Unit;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.immutables.generator.AbstractGenerator;
import org.immutables.generator.Generator.SupportedAnnotations;
import org.parboiled.Parboiled;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.errors.ParsingException;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

@SupportedAnnotations(Grammar.class)
public class Processor extends AbstractGenerator {
	final Parser parser = Parboiled.createParser(Parser.class);

	@Override
	protected void process() {
		Debug.setConsumer(message ->
				processing().getMessager().printMessage(
						Diagnostic.Kind.MANDATORY_WARNING,
						message));
		try {

			for (TypeElement t : ElementFilter.typesIn(round().getElementsAnnotatedWith(Grammar.class))) {
				String pack = packageFrom(t);
				String name = nameFrom(t);
				String filename = name + ".grammar";

				try {
					Unit unit = readGrammar(pack, filename);
					TermDispatch dispatch = TermDispatch.computeFrom(TermExpansion.collectFrom(unit));
					Vect<Production> productions = Production.collectFrom(unit);

					invoke(generator().with(t, pack, name, dispatch, productions));

				} catch (ParsingException cannotReadGrammar) {
					processing().getMessager().printMessage(
							Diagnostic.Kind.ERROR,
							pack + ": " + filename + "\n" + messageOrStacktrace(cannotReadGrammar));

				} catch (IllegalStateException illegalState) {
					processing().getMessager().printMessage(
							Diagnostic.Kind.ERROR,
							pack + ": " + filename + "\n" + messageOrStacktrace(illegalState));

				} catch (Exception ex) {
					processing().getMessager().printMessage(
							Diagnostic.Kind.ERROR,
							Throwables.getStackTraceAsString(ex));
				}
			}

		} finally {
			if (Debug.logged()) {
				processing().getMessager().printMessage(
						Diagnostic.Kind.ERROR,
						"See output");
			}
		}
	}

	private String readGrammarSource(String pack, String filename) throws Exception {
		String resourceRoot = processingEnv.getOptions().get("resources.root");
		if (resourceRoot != null) {
			String path = resourceRoot + '/' + pack.replace('.', '/') + '/' + filename;
			return new String(
					Files.readAllBytes(new File(path).toPath()),
					StandardCharsets.UTF_8);
		}
		FileObject resource = processing().getFiler().getResource(
				StandardLocation.CLASS_OUTPUT,
				pack,
				filename);

		return resource.getCharContent(true).toString();
	}

	private Unit readGrammar(String pack, String filename) throws Exception {
		String source = readGrammarSource(pack, filename);
		ParsingResult<Object> result = new ReportingParseRunner<>(parser.Grammar()).run(source);

		if (result.hasErrors()) {
			throw new ParsingException(ErrorUtils.printParseErrors(result.parseErrors));
		}

		return (Unit) result.valueStack.peek();
	}

	private String nameFrom(TypeElement type) {
		return type.getSimpleName().toString();
	}

	private String packageFrom(TypeElement type) {
		return processing().getElementUtils().getPackageOf(type).getQualifiedName().toString();
	}

	private Generator generator() throws Exception {
		Class<Generator> c = Generator.class;
		String generatorClassname = c.getPackage().getName() + ".Generator_" + c.getSimpleName();
		return (Generator) Class.forName(generatorClassname).newInstance();
	}

	private String messageOrStacktrace(Throwable exception) {
		String message = exception.getMessage();
		return Strings.isNullOrEmpty(message)
				? Throwables.getStackTraceAsString(exception)
				: message;
	}
}
