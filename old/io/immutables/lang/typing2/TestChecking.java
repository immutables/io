package io.immutables.lang.typing2;

import io.immutables.grammar.Symbol;
import io.immutables.lang.typing2.Type.Converted;
import io.immutables.lang.typing2.Type.Declared;
import io.immutables.lang.typing2.Type.Parameter;
import io.immutables.lang.typing2.Type.Product;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestChecking {
	Declared A = type("A");
	Declared B = type("B");
	Declared C = type("C");

	@Test
	public void nominal() {
		Checking.Matcher m = new Checking.Matcher();

		that(m.match(A, A)).is(true);
		that(m.match(A, B)).is(false);
		that(m.match(A, Type.Empty)).is(false);
		that(m.match(A, Product.of(A, B))).is(false);

		that(m.match(type("D", A, B), type("D", A, B))).is(true);
		that(m.match(type("D", A, B), type("D", A))).is(false);
		that(m.match(type("D", A, B), type("D", A, Type.Empty))).is(false);
	}

	@Test
	public void product() {
		Checking.Matcher m = new Checking.Matcher();

		that(m.match(Product.of(A, B), Type.Empty)).is(false);
		that(m.match(Product.of(A, B), A)).is(false);
		that(m.match(Product.of(A, B), Product.of(B, A))).is(false);
		that(m.match(Product.of(A, B), Product.of(A, B, C))).is(false);

		that(m.match(Product.of(A, B), Product.of(A, B))).is(true);

		that(m.match(Type.Empty, A)).is(false);
		that(m.match(Type.Empty, Product.of(A, B))).is(false);
	}

	@Test
	public void captureParameterNominal() {
		Parameter X = param("X");
		Parameter Y = param("Y");
		Checking.Matcher m = new Checking.Matcher(X, Y);

		that(m.match(X, A)).is(true);
		that(m.get(X)).same(A);
		that(m.get(Y)).same(Type.Undefined);

		that(m.match(type("D", A, Y), type("D", A, B))).is(true);
		that(m.get(X)).same(A);
		that(m.get(Y)).same(B);
	}

	@Test
	public void captureParameterProduct() {
		Parameter X = param("X");
		Parameter Y = param("Y");
		Checking.Matcher m = new Checking.Matcher(X, Y);

		that(m.match(Product.of(X, Y), Type.Empty)).is(false);
		that(m.match(Product.of(X, Y), Product.of(A, B))).is(true);
		that(m.get(X)).same(A);
		that(m.get(Y)).same(B);
	}

	@Test
	public void captureParameterProductNested() {
		Parameter X = param("X");
		Parameter Y = param("Y");
		Checking.Matcher m = new Checking.Matcher(X, Y);

		that(m.match(Product.of(X, Product.of(B, Y)), Product.of(A, Product.of(B, C)))).is(true);
		that(m.get(X)).same(A);
		that(m.get(Y)).same(C);
	}

	@Test
	public void coercingToNominal() {
		Checking.Coercer m = new Checking.Coercer();

		Declared D = typeCtor("D", Product.of(A, A));
		Type t = m.coerce(D, Product.of(A, A));
		that(t).instanceOf(Converted.class);
	}

	@Test
	public void coercingToProductWithParameter() {
		Parameter X = param("X");
		Checking.Coercer m = new Checking.Coercer(X);
		Declared D = typeCtor("D", Product.of(X, B), X);
		Product E = Product.of(X, D);

		Type t1 = m.coerce(E, Product.of(A, Product.of(A, B)));

		that(t1).instanceOf(Product.class).is(
				p -> p.components()[0] == A
						&& p.components()[1] instanceof Converted);
	}

	int paramCount;

	Parameter param(String name) {
		return Parameter.declare(paramCount++, Symbol.from(name));
	}

	Declared type(String name, Type... arguments) {
		return Declared.simple(Symbol.from(name), arguments);
	}

	Declared typeCtor(String name, Type in, Type... arguments) {
		return Declared.constructed(Symbol.from(name), in, arguments);
	}
}
