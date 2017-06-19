package io.immutables.lang.processor;

import com.google.common.base.CaseFormat;
import com.google.common.base.Throwables;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees.Unit;
import java.util.ArrayList;
import java.util.List;
import org.immutables.generator.AbstractTemplate;
import org.immutables.generator.Generator;
import org.immutables.generator.Templates;

@Generator.Template
abstract class SourceRuns extends AbstractTemplate {
	final List<Source> sources = new ArrayList<>();
	String packageName;
	String fixtureName;

	static class Source {
		final String name;
		final String content;
		final boolean failed;
		final String message;
		final String id;

		Source(String name, String content, boolean failed, String message) {
			this.name = name;
			this.content = content;
			this.failed = failed;
			this.message = message;
			this.id = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name);
		}
	}

	void process(String name, String filename, String content) {
		try {
			SyntaxTerms terms = SyntaxTerms.from(content.toCharArray());
			SyntaxProductions<Unit> productions = SyntaxProductions.unit(terms);
			if (productions.ok()) {
				// this should not fail in theory,
				// but any exceptions will be caught by the exception handler
				productions.construct();
				sources.add(new Source(name, content, false, ""));
			} else {
				sources.add(new Source(name, content, true, productions.messageForFile(filename)));
			}
		} catch (Exception ex) {
			sources.add(new Source(name, content, true, Throwables.getStackTraceAsString(ex)));
		}
	}

	abstract Templates.Invokable generate();
}
