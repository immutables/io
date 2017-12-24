package io.immutables.lang.typing;

import io.immutables.grammar.Symbol;
import io.immutables.lang.typing.Type.Nominal;
import io.immutables.lang.typing.Type.Parameter;
import io.immutables.lang.typing.Type.Product;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestChecking {
	Nominal A = type("A");
	Nominal B = type("B");
	Nominal C = type("C");

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

	int paramCount;

	Parameter param(String name) {
		return Parameter.declare(paramCount++, Symbol.from(name));
	}

	Nominal type(String name, Type... arguments) {
		return Nominal.simple(Symbol.from(name), arguments);
	}
}
