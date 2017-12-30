package io.immutables.lang.type.fixture;

import io.immutables.collect.Vect;
import io.immutables.grammar.Symbol;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees;
import io.immutables.lang.SyntaxTrees.Argument;
import io.immutables.lang.SyntaxTrees.ComponentExpression;
import io.immutables.lang.SyntaxTrees.Expression;
import io.immutables.lang.SyntaxTrees.ExpressionAccess;
import io.immutables.lang.SyntaxTrees.ExpressionFeature;
import io.immutables.lang.SyntaxTrees.ExpressionOrStatement;
import io.immutables.lang.SyntaxTrees.LiteralNumberDecimal;
import io.immutables.lang.SyntaxTrees.LiteralProduct;
import io.immutables.lang.SyntaxTrees.LiteralString;
import io.immutables.lang.type.DefinedImpl;
import io.immutables.lang.type.Name;
import io.immutables.lang.type.Type22;
import io.immutables.lang.type.Type22.Constructor;
import io.immutables.lang.type.Type22.Feature;
import io.immutables.lang.type.Type22.Product;
import io.immutables.lang.type.Type22.Variable;
import io.immutables.lang.type.fixture.Node33.StructuralMismatch;
import org.junit.Test;
import static io.immutables.that.Assert.that;

// a = a.b(1, 2 + c)
// how?
// starting at let binding we determine target type W from the left
// then we determine type of the expression on the right
// go down recursively???
//
//
public class TestTypecheck {

//	@Test
//	public void parseCheck() {
//		Expression expression = expression("a().b(1, 2 + c(d))");
//		System.out.println(expression);
//	}
//
//	@Test
//	public void parseCheck2() {
//		Expression expression = expression("a{b: 1, c: 2 + d}");
//		System.out.println(expression);
//	}

//	@Test
//	public void typeCheck3() {
//		Expression expression = expression("a.b(1, 2 + c)");
//		System.out.println(expression);
//	}

//	@Test
//	public void typeCheck() {
//		Expression expression = expression("a.b(1, \"c\")");
//		System.out.println(expression);
//	}
	abstract static class Matcher extends SyntaxTrees.Matcher<Type22, Node33> {
		protected final Scope scope;

		Matcher(Scope scope) {
			this.scope = scope;
		}
	}

	@Test
	public void typeMatch() {
		Variable X = Type22.Variable.allocate(Name.of("X"));
		Variable Y = Type22.Variable.allocate(Name.of("Y"));

		TypeMatcher m = new TypeMatcher(X, Y);
		that(m.match(IntType, StringType)).is(false);

		m = new TypeMatcher(X, Y);
		that(m.match(Type22.Product.of(X, Y), Type22.Product.of(StringType, IntType))).is(true);

		m = new TypeMatcher(X);
		that(m.match(Type22.Product.of(X, X), Type22.Product.of(IntType, IntType))).is(true);

		m = new TypeMatcher(Y);
		that(m.match(typeArg("B", Y, Y), typeArg("B", IntType, IntType))).is(true);
	}

	// @Test
	public void typeSimplest() {
		Expression expression = expression("a.b(1, \"c\")");
		System.out.println(expression);

		Scope letA = Scope.init()
				.let(Name.of("a"), Node33.StaticValue.of(-1, "<a>", AType));

		Nodester nodester = new Nodester(letA);
		Node33 match = nodester.match(expression, Type22.Undefined);

		that(match).hasToString("<scope>.a: A.b((1: Int, \"c\": String): (Int, String)): Int");

		System.out.println(match);
	}

	@Test
	public void typeSimplest2() {
//		Expression expression = expression("a.b(1, \"c\")");
//		System.out.println(expression);
//
//		Scope letA = Scope.init()
//				.let(Name.of("a"), Node.StaticValue.of(-1, "<a>", AType));
//
//		Nodester nodester = new Nodester(letA);
//		Node match = nodester.match(expression, Type.Undefined);
//
//		that(match).hasToString("<scope>.a: A.b((1: Int, \"c\": String): (Int, String)): Int");
	}

	static final Type22.Declared IntType = new DefinedImpl.Builder()
			.name(Name.of("Int"))
			.build();

	static final Type22.Declared StringType = new DefinedImpl.Builder()
			.name(Name.of("String"))
			.build();

	static DefinedImpl typeArg(String name, Type22... argument) {
		return new DefinedImpl.Builder()
				.name(Name.of(name))
				.addArguments(argument)
				.build();
	}

	static final Type22.Declared AType = new DefinedImpl.Builder()
			.name(Name.of("A"))
			.addFeatures(Type22.Feature.simple(Name.of("b"), Type22.Product.of(IntType, StringType), IntType))
			.build();

//
//	private Type applyArrow(Feature cF, Type in) {
//		return null;
//	}

//	interface Value {
//		Type type();
//
//		default Value apply(Name name, Value input) {
//			Type type = type();
//			Feature feature = type.getFeature(name);
//			// feature
////			Type out = inTP.accept(conformer, inTA);
//
//			return new Value() {
//
//				@Override
//				public Type type() {
//					return out;
//				}
//			};
//		}
//
//		static <F extends Type.Arrow<F>> F typecheck(F arrow, Type type) {
//			return arrow;
//		}
//	}

//	static class FeatureApplied implements Val {
//		private final Val receiver;
//		private final Val in;
//
//		public FeatureApplied(Val receiver, Name name, Val in) {
//			this.receiver = receiver;
//			this.in = in;
//		}
//
//		@Override
//		public Type type() {
//			return null;
//		}
//
//		static Val apply(Val receiver, Name name, Val in) {
//			Feature feature = receiver.type().getFeature(name);
//			Type inTP = feature.in();
//			Type inTA = in.type();
//
//			Conformer conformer = new Conformer();
//			Type type = inTP.accept(conformer, inTA);
//
//			// return new FeatureApplied(receiver, n, in);
//		}
//	}

//	static final class Conformer implements Type.Visitor<Type, Type> {
//		@Override
//		public Type declared(Declared to, Type in) {
//			return in.accept(new Type.Visitor<Type, Type>() {
//				@Override
//				public Type declared(Declared from, Type in) {
//					if (to.name().equals(from.name())) {
//						to.arguments().equals(from.arguments());
//
//					}
//					return in;
//				}
//			}, in);
//		}
//	}
//
//	final class Scope {}
//
//	interface Context extends Type {}

	private static Expression expression(String code) {
		SyntaxTerms terms = SyntaxTerms.from(code.toCharArray());
		SyntaxProductions<Expression> productions = SyntaxProductions.expression(terms);
		System.out.println(productions.show());
		if (productions.ok()) {
			return productions.construct();
		}
		System.out.println(productions.message());
		throw new AssertionError(productions.messageForFile("<expression>"));
	}

	private static Name nameFrom(Symbol symbol) {
		return Name.of(symbol.toString());
	}

	public final class Nodester extends SyntaxTrees.Matcher<Type22, Node33> {
		final Scope scope;

		Nodester(Scope scope) {
			this.scope = scope;
		}

		@Override
		public Node33 caseExpressionAccess(ExpressionAccess access, Type22 lastExpected) {
			assert !access.selector().isEmpty();

			Node33 receiver = caseExpressionFeatureSub(access.base(), Type22.Undefined);

			for (Vect<ExpressionFeature>.Iterator it = access.selector().iterator(); it.hasNext();) {
				ExpressionFeature ef = it.next();
				Type22 expected = it.isLast() ? lastExpected : Type22.Undefined;
				receiver = feature(ef, receiver, expected);
			}

			return receiver;
		}

		@Override
		public Node33 caseExpressionFeature(ExpressionFeature fe, Type22 expected) {
			// should not be called from inside caseExpressionAccess
			return feature(fe, scope, expected);
		}

		private Node33 feature(ExpressionFeature expression, Node33 receiver, Type22 expected) {
			Feature f = receiver.type().getFeature(nameFrom(expression.name()));

			if (expression.argument().isPresent()) {
				Argument arg = expression.argument().get();

				assert arg instanceof ExpressionOrStatement : "Have grammar structure changed?";

				Node33 input = f.in().accept(new Conformer(), (ExpressionOrStatement) arg);

				if (!input.checks()) {
					return Node33.UnapplicableFeature.of(expression.productionIndex(), receiver, f, input);
				}
				if (expected != Type22.Undefined && !f.out().eq(expected)) {
					return Node33.TypeMismatch.of(expression.productionIndex(), expected);
				}
				return Node33.ApplyFeature.of(expression.productionIndex(), receiver, f, input);
			}

			if (!f.in().eq(Type22.Empty)) {
				return Node33.UnapplicableFeature.of(expression.productionIndex(), receiver, f, Node33.Empty);
			}
			if (expected != Type22.Undefined && !f.out().eq(expected)) {
				return Node33.TypeMismatch.of(expression.productionIndex(), expected);
			}
			return Node33.ApplyFeature.of(expression.productionIndex(), receiver, f, Node33.Empty);
		}

		@Override
		public Node33 caseLiteralString(LiteralString v, Type22 expected) {
			if (expected != Type22.Undefined && !expected.eq(TestTypecheck.StringType)) {
				return Node33.TypeMismatch.of(v.productionIndex(), expected);
			}
			return Node33.StaticValue.of(v.productionIndex(), v.literal(), TestTypecheck.StringType);
		}

		@Override
		public Node33 caseLiteralNumberDecimal(LiteralNumberDecimal v, Type22 expected) {
			if (expected != Type22.Undefined && !expected.eq(TestTypecheck.IntType)) {
				return Node33.TypeMismatch.of(v.productionIndex(), expected);
			}
			return Node33.StaticValue.of(v.productionIndex(), v.literal(), TestTypecheck.IntType);
		}

		final class Conformer implements Type22.Visitor<ExpressionOrStatement, Node33> {
			@Override
			public Node33 undefined(ExpressionOrStatement in) {
				return Nodester.this.caseExpressionOrStatement(in, Type22.Undefined);
			}

			@Override
			public Node33 product(Product p, ExpressionOrStatement in) {
				if (in instanceof SyntaxTrees.LiteralProduct) {
					Vect<ComponentExpression> expressions = ((SyntaxTrees.LiteralProduct) in).component();
					// TODO how to make flexible matching (for splatting, varargs)
					int size = p.components().size();
					if (size != expressions.size()) {
						return StructuralMismatch.of(in.productionIndex(), p);
					}
					Node33[] components = new Node33[size];
					Type22[] types = new Type22[size]; // recreating types because of generic matching
					for (int i = 0; i < size; i++) {
						Type22 expected = p.components().get(i);
						ExpressionOrStatement expression = expressions.get(i).value();
						// Node c = Nodester.this.caseExpressionOrStatement(expression, expected);
						Node33 c = expected.accept(this, expression);
						if (c.type() == Type22.Undefined) {
							return StructuralMismatch.of(expression.productionIndex(), expected);
						}
						components[i] = c;
						types[i] = c.type();
					}
					return Node33.ConstructProduct.of(in.productionIndex(), Type22.Product.of(types), components);
				}
				return reconstruct(p, in);
			}

			@Override
			public Node33 empty(ExpressionOrStatement in) {
				if (in instanceof SyntaxTrees.LiteralProduct
						&& ((SyntaxTrees.LiteralProduct) in).component().isEmpty()) {
					return Node33.Empty;
				}
				return otherwise(Type22.Empty, in);
			}

			@Override
			public Node33 otherwise(Type22 t, ExpressionOrStatement in) {
				if (in instanceof SyntaxTrees.LiteralProduct) {
					LiteralProduct p = (SyntaxTrees.LiteralProduct) in;
					// Not sure this should be done on this level
					// (and this might never be the case depending on how parser works)
					// but we need to check the usual case of single component product which
					// is the component itself
					if (p.component().size() == 1) {
						return t.accept(this, p.component().get(0).value());
					}
				}
				return reconstruct(t, in);
			}

			private Node33 reconstruct(Type22 t, ExpressionOrStatement in) {
				Node33 n = Nodester.this.caseExpressionOrStatement(in, t);

				if (n.type() == Type22.Empty && t instanceof Type22.Declared) {
					Vect<Constructor> constr = ((Type22.Declared) t).constructors();
				}
				return Node33.NotApplicable;
			}
		}
	}
}
