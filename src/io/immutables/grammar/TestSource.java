package io.immutables.grammar;

import io.immutables.grammar.Source.Lines;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestSource {
	final char[] input = ("01\n"
			+ "345\n"
			+ "789").toCharArray();
	final CharSequence source = Source.wrap(input);

	@Test
	public void positions() {
		Lines lines = Lines.from(input);
		that(lines.get(2)).hasToString("1:3");
		that(lines.get(3)).hasToString("2:1");
		that(lines.get(7)).hasToString("3:1");
		that(lines.get(9)).hasToString("3:3");
	}

	@Test
	public void lines() {
		Lines lines = Lines.from(input);
		that(lines.count()).is(3);
		that(lines.getLine(source, 1).get()).hasToString("01");
		that(lines.getLine(source, 2).get()).hasToString("345");
		that(lines.getLine(source, 3).get()).hasToString("789");
	}
	
	
}
