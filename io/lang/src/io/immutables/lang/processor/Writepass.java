package io.immutables.lang.processor;

import com.squareup.javapoet.MethodSpec;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees.Unit;
import org.immutables.generator.AbstractTemplate;
import org.immutables.generator.Templates;
import org.immutables.generator.Templates.Invokation;

public final class Writepass extends AbstractTemplate {
	private final String filename;
	private final String content;

	public Writepass(String filename, String content) {
		this.filename = filename;
		this.content = content;
	}

	public final Templates.Invokable template = new Templates.Fragment(0) {
		@Override
		public void run(Invokation inv) {
			SyntaxTerms terms = SyntaxTerms.from(content.toCharArray());
			SyntaxProductions<Unit> productions = SyntaxProductions.unit(terms);

			// String packageName = parameters[0].toString();
			// String simpleName = parameters[1].toString();
			// Element originatingElement = (Element) parameters[2];
			// Invokable body = (Invokable) parameters[3];
			// output.java.invoke(inv, null);
		}
	};

	public static void main(String... args) {
		MethodSpec build = MethodSpec.methodBuilder("abc")
				.addParameter(int[].class, "x")
				.returns(void.class)
				.varargs()
				.addCode("x.clone();")
				.build();
		
		System.out.println(build);
	}
}
