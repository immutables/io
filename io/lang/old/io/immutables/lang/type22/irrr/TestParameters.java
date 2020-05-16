package io.immutables.lang.type22.irrr;

import java.util.NoSuchElementException;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestParameters {
	final Parameters empty = Parameters.empty();
	final Parameters withA = empty.introduce("A");
	final Parameters withABC = withA.introduce("B").introduce("C");
	final Parameters withADE = withA.introduce("D").introduce("E");

	@Test
	public void compositionAndToString() {
		that(empty).hasToString("<>");
		that(withA).hasToString("<A>");
		that(withABC).hasToString("<A,B,C>");
		that(withADE).hasToString("<A,D,E>");
	}

	@Test
	public void get() {
		that(() -> empty.get()).thrown(NoSuchElementException.class);
		that(withA.get().name()).equalTo("A");
		that(withABC.get().name()).equalTo("C");
		that(withADE.get().name()).equalTo("E");
	}

	@Test
	public void unwind() {
		that(withA.unwind().map(Object::toString)).isOf("A");
		that(withABC.unwind().map(Object::toString)).isOf("A", "B", "C");
		that(withADE.unwind().map(Object::toString)).isOf("A", "D", "E");
	}

	@Test
	public void find() {
		that(empty.find("A")).isEmpty();
		that(withA.find("A")).isOf(withA.get());
		that(withABC.find("D")).isEmpty();
		that(withADE.find("E")).isOf(withADE.get());
	}
}
