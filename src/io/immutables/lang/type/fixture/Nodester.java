package io.immutables.lang.type.fixture;

import io.immutables.collect.Vect;
import io.immutables.grammar.Symbol;
import io.immutables.lang.SyntaxTrees;
import io.immutables.lang.SyntaxTrees.Argument;
import io.immutables.lang.SyntaxTrees.ComponentExpression;
import io.immutables.lang.SyntaxTrees.ExpressionAccess;
import io.immutables.lang.SyntaxTrees.ExpressionFeature;
import io.immutables.lang.SyntaxTrees.ExpressionOrStatement;
import io.immutables.lang.SyntaxTrees.LiteralNumberDecimal;
import io.immutables.lang.SyntaxTrees.LiteralProduct;
import io.immutables.lang.SyntaxTrees.LiteralString;
import io.immutables.lang.type.Name;
import io.immutables.lang.type.Type;
import io.immutables.lang.type.Type.Feature;
import io.immutables.lang.type.Type.Product;
import io.immutables.lang.type.fixture.Node.StructuralMismatch;

public final class Nodester extends SyntaxTrees.Matcher<Type, Node> {
	final Scope scope;

	Nodester(Scope scope) {
		this.scope = scope;
	}

	@Override
	public Node caseExpressionAccess(ExpressionAccess access, Type lastExpected) {
		assert !access.selector().isEmpty();

		Node receiver = caseExpressionFeatureSub(access.base(), Type.Undefined);

		for (Vect<ExpressionFeature>.Iterator it = access.selector().iterator(); it.hasNext();) {
			ExpressionFeature ef = it.next();
			Type expected = it.isLast() ? lastExpected : Type.Undefined;
			receiver = feature(ef, receiver, expected);
		}

		return receiver;
	}

	@Override
	public Node caseExpressionFeature(ExpressionFeature fe, Type expected) {
		// should not be called from inside caseExpressionAccess
		return feature(fe, scope, expected);
	}

	private Node feature(ExpressionFeature expression, Node receiver, Type expected) {
		Feature f = receiver.type().getFeature(nameFrom(expression.name()));

		if (expression.argument().isPresent()) {
			Argument arg = expression.argument().get();

			Node input = arg instanceof ExpressionOrStatement
					? f.in().accept(new Conformer(), (ExpressionOrStatement) arg)
					: caseArgument(arg, f.in());

			if (input.type() == Type.Undefined) {
				return Node.UnapplicableFeature.of(expression.productionIndex(), receiver, f, input);
			}
			if (expected != Type.Undefined && !f.out().eq(expected)) {
				return Node.TypeMismatch.of(expression.productionIndex(), expected);
			}
			return Node.ApplyFeature.of(expression.productionIndex(), receiver, f, Node.Empty);
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
		if (expected != Type.Undefined && !expected.eq(TestTypecheck.StringType)) {
			return Node.TypeMismatch.of(v.productionIndex(), expected);
		}
		return Node.StaticValue.of(v.productionIndex(), v.literal().unquote(), TestTypecheck.StringType);
	}

	@Override
	public Node caseLiteralNumberDecimal(LiteralNumberDecimal v, Type expected) {
		if (expected != Type.Undefined && !expected.eq(TestTypecheck.IntType)) {
			return Node.TypeMismatch.of(v.productionIndex(), expected);
		}
		return Node.StaticValue.of(v.productionIndex(), v.literal(), TestTypecheck.IntType);
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
				int size = p.components().size();
				if (size != expressions.size()) {
					return StructuralMismatch.of(in.productionIndex());
				}
				Node[] components = new Node[size];
				Type[] types = new Type[size]; // recreating types because of generic matching
				for (int i = 0; i < size; i++) {
					Type expected = p.components().get(i);
					ExpressionOrStatement expression = expressions.get(i).value();
					Node c = expected.accept(this, expression);
					if (c.type() == Type.Undefined) {
						return StructuralMismatch.of(expression.productionIndex());
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
			Node n = undefined(in);

			if (t instanceof Type.Declared) {
				((Type.Declared) t).constructors();
				// TODO
			}
			return Node.NotApplicable;
		}
	}

	private static Name nameFrom(Symbol symbol) {
		return Name.of(symbol.toString());
	}
}
