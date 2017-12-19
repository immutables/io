package io.immutables.lang.type.fixture;

import io.immutables.grammar.Symbol;
import io.immutables.lang.SyntaxProductions;
import io.immutables.lang.SyntaxTerms;
import io.immutables.lang.SyntaxTrees;
import io.immutables.lang.SyntaxTrees.Expression;
import io.immutables.lang.type.DefinedImpl;
import io.immutables.lang.type.Name;
import io.immutables.lang.type.Type;
import io.immutables.lang.type.Type.Variable;
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
	abstract static class Matcher extends SyntaxTrees.Matcher<Type, Node> {
		protected final Scope scope;

		Matcher(Scope scope) {
			this.scope = scope;
		}
	}

	@Test
	public void typeMatch() {
		Variable X = Type.Variable.allocate(Name.of("X"));
		Variable Y = Type.Variable.allocate(Name.of("Y"));

		TypeMatcher m = new TypeMatcher(X, Y);
		that(m.match(IntType, StringType)).is(false);

		m = new TypeMatcher(X, Y);
		that(m.match(Type.Product.of(X, Y), Type.Product.of(StringType, IntType))).is(true);

		m = new TypeMatcher(X);
		that(m.match(Type.Product.of(X, X), Type.Product.of(IntType, IntType))).is(true);

		m = new TypeMatcher(Y);
		that(m.match(typeArg("B", Y, Y), typeArg("B", IntType, IntType))).is(true);
	}

	@Test
	public void typeCheck() {
		Expression expression = expression("a.b()");
		System.out.println(expression);

		Scope letA = Scope.init()
				.let(Name.of("a"), Node.StaticValue.of(-1, "<a>", AType));

		Nodester nodester = new Nodester(letA);
		Node match = nodester.match(expression, Type.Undefined);
		System.out.println(match);

		// Context s = null;
		// Name name = Name.of("c");
		// Feature cF = s.getFeature(name);
		// Maybe replace with null object pattern with unresolved
		// named feature
		// Type out = applyArrow(cF, Type.Empty);
	}

	static final Type.Declared IntType = new DefinedImpl.Builder()
			.name(Name.of("Int"))
			.build();

	static final Type.Declared StringType = new DefinedImpl.Builder()
			.name(Name.of("String"))
			.build();

	static DefinedImpl typeArg(String name, Type... argument) {
		return new DefinedImpl.Builder()
				.name(Name.of(name))
				.addArguments(argument)
				.build();
	}

	static final Type.Declared AType = new DefinedImpl.Builder()
			.name(Name.of("A"))
			.addFeatures(Type.Feature.simple(Name.of("b"), Type.Product.of(IntType, StringType), IntType))
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

}
