package io.immutables.source;

import io.immutables.source.Source.Lines;
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
	public void range() {
		Lines ls = Lines.from("\nasas".toCharArray());
		that(ls.getLineRange(1)).hasToString("[1:1)");
	}

	@Test
	public void lines() {
		Lines lines = Lines.from(input);
		that(lines.count()).is(3);
		that(lines.getLineRange(1).get(source)).hasToString("01");
		that(lines.getLineRange(2).get(source)).hasToString("345");
		that(lines.getLineRange(3).get(source)).hasToString("789");
	}
}
