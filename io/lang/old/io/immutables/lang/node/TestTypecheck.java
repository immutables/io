package io.immutables.lang.node;

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
import io.immutables.lang.node.Node.StructuralMismatch;
import io.immutables.lang.node.Type.Constructor;
import io.immutables.lang.node.Type.Product;
import org.junit.Ignore;
import org.junit.Test;

//a = a.b(1, 2 + c)
//how?
//starting at let binding we determine target type W from the left
//then we determine type of the expression on the right
//go down recursively???
public class TestTypecheck {

	@Test
	@Ignore
	public void parseCheck() {
		Expression expression = expression("a().b(1, 2 + c(d))");
		System.out.println(expression);
		Expression expression1 = expression("a{b: 1, c: 2 + d}");
		System.out.println(expression1);
		Expression expression2 = expression("a.b(1, 2 + c)");
		System.out.println(expression2);
		Expression expression3 = expression("a.b(1, \"c\")");
		System.out.println(expression3);
		Expression expression4 = expression("a.b.c.d(1).e(\"f\").g");
		System.out.println(expression4);
	}

	@Test
	public void feat1() {
		Expression expression3 = expression("a.b(1, \"c\")");
	}

	private static Expression expression(String code) {
		SyntaxTerms terms = SyntaxTerms.from(code.toCharArray());
		SyntaxProductions<Expression> productions = SyntaxProductions.expression(terms);
		System.out.println(productions.show());
		if (productions.ok()) {
			return productions.construct();
		}
		System.err.println(productions.message());
		throw new AssertionError(productions.messageForFile("<expression>"));
	}

	static final Type.Declared IntType = Impl.declared(Name.of("Int"));

	static final Type.Declared StringType = Impl.declared(Name.of("String"));

	static final Type.Declared AType = Impl.declared(Name.of("A"),
			Vect.of(Type.Feature.simple(Name.of("b"),
					Type.Product.of(IntType, StringType), IntType)));

	public static final class Nodester extends SyntaxTrees.Matcher<Type, Node> {
		final Scope scope;

		Nodester(Scope scope) {
			this.scope = scope;
		}

		private static Name nameFrom(Symbol symbol) {
			return Name.of(symbol.toString());
		}

		@Override
		public Node caseExpressionAccess(ExpressionAccess access, Type lastExpected) {
			assert !access.selector().isEmpty();

			Node receiver = match(access.base(), Type.Undefined);

			for (Vect<ExpressionFeature>.Iterator it = access.selector().iterator(); it.hasNext();) {
				ExpressionFeature expression = it.next();
				Type expected = it.isLast() ? lastExpected : Type.Undefined;
				receiver = applyFeature(expression, receiver, expected);
			}

			return receiver;
		}

		@Override
		public Node caseExpressionFeature(ExpressionFeature expression, Type expected) {
			// should not be called from inside caseExpressionAccess
			return applyFeature(expression, scope, expected);
		}

		private Node applyFeature(ExpressionFeature expression, Node receiver, Type expected) {
			Type.Feature f = receiver.type().getFeature(nameFrom(expression.name()));

			if (expression.argument().isPresent()) {
				Argument arg = expression.argument().get();

				assert arg instanceof ExpressionOrStatement : "Have grammar structure changed?";

				Node input = f.in().accept(new Conformer(), (ExpressionOrStatement) arg);

				if (!input.checks()) {
					return Node.UnapplicableFeature.of(expression.productionIndex(), receiver, f, input);
				}
				if (expected != Type.Undefined && !f.out().eq(expected)) {
					return Node.TypeMismatch.of(expression.productionIndex(), expected);
				}
				return Node.ApplyFeature.of(expression.productionIndex(), receiver, f, input);
			}

			if (!f.in().eq(Type.Empty)) {
				return Node.UnapplicableFeature.of(expression.productionIndex(), receiver, f, Node.Empty);
			}
			if (expected != Type.Undefined && !f.out().eq(expected)) {
				return Node.TypeMismatch.of(expression.productionIndex(), expected);
			}
			return Node.ApplyFeature.of(expression.productionIndex(), receiver, f, Node.Empty);
		}

		@Override
		public Node caseLiteralString(LiteralString v, Type expected) {
			if (expected != Type.Undefined && !expected.eq(StringType)) {
				return Node.TypeMismatch.of(v.productionIndex(), expected);
			}
			return Node.StaticValue.of(v.productionIndex(), v.literal(), StringType);
		}

		@Override
		public Node caseLiteralNumberDecimal(LiteralNumberDecimal v, Type expected) {
			if (expected != Type.Undefined && !expected.eq(IntType)) {
				return Node.TypeMismatch.of(v.productionIndex(), expected);
			}
			return Node.StaticValue.of(v.productionIndex(), v.literal(), IntType);
		}

		final class Conformer implements Type.Visitor<ExpressionOrStatement, Node> {
			@Override
			public Node undefined(ExpressionOrStatement in) {
				return Nodester.this.caseExpressionOrStatement(in, Type.Undefined);
			}

			@Override
			public Node product(Product p, ExpressionOrStatement in) {
				if (in instanceof SyntaxTrees.LiteralProduct) {
					Vect<ComponentExpression> expressions = ((SyntaxTrees.LiteralProduct) in).component();
					// TODO how to make flexible matching (for splatting, varargs)
					Type[] ptypes = p.components();
					int size = ptypes.length;
					if (size != expressions.size()) {
						return StructuralMismatch.of(in.productionIndex(), p);
					}
					Node[] components = new Node[size];
					Type[] types = new Type[size]; // recreating types because of generic matching
					for (int i = 0; i < size; i++) {
						Type expected = ptypes[i];
						ExpressionOrStatement expression = expressions.get(i).value();
						// Node c = Nodester.this.caseExpressionOrStatement(expression, expected);
						Node c = expected.accept(this, expression);
						if (c.type() == Type.Undefined) {
							return StructuralMismatch.of(expression.productionIndex(), expected);
						}
						components[i] = c;
						types[i] = c.type();
					}
					return Node.ConstructProduct.of(in.productionIndex(), Type.Product.of(types), components);
				}
				return reconstruct(p, in);
			}

			@Override
			public Node empty(ExpressionOrStatement in) {
				if (in instanceof SyntaxTrees.LiteralProduct
						&& ((SyntaxTrees.LiteralProduct) in).component().isEmpty()) {
					return Node.Empty;
				}
				return otherwise(Type.Empty, in);
			}

			@Override
			public Node otherwise(Type t, ExpressionOrStatement in) {
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

			private Node reconstruct(Type t, ExpressionOrStatement in) {
				Node n = Nodester.this.caseExpressionOrStatement(in, t);

				if (n.type() == Type.Empty && t instanceof Type.Declared) {
					Constructor[] constructors = ((Type.Declared) t).constructors();
				}
				return Node.NotApplicable;
			}
		}
	}
}
