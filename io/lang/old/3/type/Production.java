package io.immutables.lang.type;

import io.immutables.collect.Vect;

interface Production {

	interface Expression extends Production {}

	class FeatureApply implements Expression {
		String name;
		Expression on;
		Expression in;

		@Override public String toString() {
			if (name.equals("+")) return on + " + " + in;
			return on + "." + name + (in instanceof Product ? in : ("(" + in + ")"));
		}
	}

	class Access implements Expression {
		String name;

		@Override public String toString() {
			return "$" + name;
		}
	}

	interface Literal extends Expression {}

	class NumberLiteral implements Literal {
		int value;

		@Override public String toString() {
			return Integer.toString(value);
		}
	}

	interface DelimitedLiteral extends Literal {}

	class Product implements DelimitedLiteral {
		Vect<Expression> components;

		@Override public String toString() {
			return components.join(", ", "(", ")");
		}
	}

	class Traversal {

		protected void caseProduction(Production production) {
			if (production instanceof Expression) {
				caseExpression((Expression) production);
			}
		}

		protected void caseExpression(Expression expression) {
			if (expression instanceof Literal) {
				caseLiteral((Literal) expression);
			} else if (expression instanceof Access) {
				caseAccess((Access) expression);
			} else if (expression instanceof FeatureApply) {
				caseFeatureApply((FeatureApply) expression);
			}
		}

		protected void caseFeatureApply(FeatureApply apply) {
			featureName(apply.name);
			featureOn(apply.on);
			featureIn(apply.in);
		}

		protected void featureName(String name) {}

		protected void featureIn(Expression expression) {
			caseExpression(expression);
		}

		protected void featureOn(Expression expression) {
			caseExpression(expression);
		}

		protected void caseAccess(Access access) {
			accessName(access.name);
		}

		protected void accessName(String name) {}

		protected void caseLiteral(Literal literal) {
			if (literal instanceof Product) {
				caseProduct((Product) literal);
			} else if (literal instanceof NumberLiteral) {
				caseNumberLiteral((NumberLiteral) literal);
			}
		}

		protected void caseNumberLiteral(NumberLiteral number) {
			numericValue(number.value);
		}

		protected void numericValue(int value) {}

		protected void caseProduct(Product product) {
			productComponents(product.components);
		}

		protected void productComponents(Vect<Expression> components) {
			for (var c : components) caseExpression(c);
		}
	}
}
