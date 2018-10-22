package io.immutables.lang.node;

import io.immutables.lang.node.Type.Declared;
import io.immutables.lang.node.Type.Parameter;
import io.immutables.lang.node.Type.Product;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestMatcher {
	Declared A = type("A");
	Declared B = type("B");
	Declared C = type("C");

	@Test
	public void nominal() {
		Matcher m = new Matcher();

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
		Matcher m = new Matcher();

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
		Matcher m = new Matcher(X, Y);

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
		Matcher m = new Matcher(X, Y);

		that(m.match(Product.of(X, Y), Type.Empty)).is(false);
		that(m.match(Product.of(X, Y), Product.of(A, B))).is(true);
		that(m.get(X)).same(A);
		that(m.get(Y)).same(B);
	}

	@Test
	public void captureParameterProductNested() {
		Parameter X = param("X");
		Parameter Y = param("Y");
		Matcher m = new Matcher(X, Y);

		that(m.match(Product.of(X, Product.of(B, Y)), Product.of(A, Product.of(B, C)))).is(true);
		that(m.get(X)).same(A);
		that(m.get(Y)).same(C);
	}

	int paramCount;

	Parameter param(String name) {
		return Parameter.declare(paramCount++, Name.of(name));
	}

	Declared type(String name, Type... arguments) {
		return Declared.simple(Name.of(name), arguments);
	}
}
