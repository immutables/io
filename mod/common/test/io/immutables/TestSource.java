package io.immutables;

import io.immutables.Source.Buffer;
import io.immutables.Source.Lines;
import java.nio.CharBuffer;
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
		that(Lines.from("\nasas".toCharArray()).getLineRange(1)).hasToString("[1:1)");
		that(Lines.from("\nasas").getLineRange(1)).hasToString("[1:1)");
	}

	@Test
	public void lines() {
		Lines lines = Lines.from(input);
		that(lines.count()).is(3);
		that(lines.getLineRange(1).get(source)).hasToString("01");
		that(lines.getLineRange(2).get(source)).hasToString("345");
		that(lines.getLineRange(3).get(source)).hasToString("789");
	}

	@Test
	public void buffer() {
		Buffer b = new Buffer();
		b.append('_');
		b.append('A');
		b.append("BCD");
		b.append(new StringBuilder("EF"));
		b.write('G');
		b.write('H');
		b.append(CharBuffer.wrap("IJK".toCharArray()));
		b.append(Source.wrap("LMNO".toCharArray()));
		b.append("__PQ__", 2, 4);
		b.write("RS");
		b.write("RST", 2, 1);
		b.write("UVW".toCharArray());
		b.write("XY&&".toCharArray(), 0, 2);
		b.append(new StringBuffer(">>Z"), 2, 3); // not optimized per char copy
		b.append("!_", 1, 2);

		that(b.length()).is(28);
		that(b.charAt(0)).is('_');
		that(b.charAt(b.length() - 1)).is('_');
		that(b).hasToString("_ABCDEFGHIJKLMNOPQRSTUVWXYZ_");
		that(b.subSequence(1, 4)).hasToString("ABC");
		that(b.subSequence(3, 8).subSequence(1, 4)).hasToString("DEF");

		that(() -> b.subSequence(-1, -2)).thrown(IndexOutOfBoundsException.class);
		that(() -> b.subSequence(2, 1)).thrown(IndexOutOfBoundsException.class);
		that(() -> b.subSequence(0, 100)).thrown(IndexOutOfBoundsException.class);
	}
}
