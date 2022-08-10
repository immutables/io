package io.immutables.lang.type;

public interface Visitor<I, O> {
	default O variable(Type.Variable v, I in) {
		return otherwise(v, in);
	}

	default O basic(Type.Basic b, I in) {
		return otherwise(b, in);
	}

	default O nominal(Type.Nominal d, I in) {
		return otherwise(d, in);
	}

	default O product(Type.Product p, I in) {
		return otherwise(p, in);
	}

	default O empty(I in) {
		return otherwise(Type.Product.Empty, in);
	}

	default O undefined(I in) {
		return otherwise(Type.Undefined, in);
	}
	/*
      default O unresolved(Unresolved f, I in) {
        return otherwise(f, in);
      }
  */
	default O otherwise(Type t, I in) {
		throw new UnsupportedOperationException("cannot handle type " + t + " with input: " + in);
	}

	abstract class Traverse<I> implements Visitor<I, Void> {
		@Override public Void nominal(Type.Nominal d, I in) {
			d.arguments().forEach(a -> a.accept(this, in));
			return null;
		}

		@Override public Void product(Type.Product p, I in) {
			p.components().forEach(a -> a.accept(this, in));
			return null;
		}

		@Override public Void otherwise(Type t, I in) {
			return null;
		}
	}

	abstract class Transform<I> implements Visitor<I, Type> {
		@Override public Type nominal(Type.Nominal d, I in) {
			var tystructor = switchTystructor(d.tystructor(), in);
			var arguments = d.arguments().map(t -> t.accept(this, in));
			return tystructor.construct(arguments);
		}

		@Override public Type product(Type.Product p, I in) {
			var components = p.components().map(t -> t.accept(this, in));
			return Types.product(components);
		}

		@Override public Type variable(Type.Variable v, I in) {
			return replace(v, in);
		}

		@Override public Type otherwise(Type t, I in) {
			return transform(t, in);
		}

		protected Type replace(Type.Variable t, I in) {return transform(t, in);}

		protected Type transform(Type t, I in) {return t;}

		protected Type.Tystructor switchTystructor(Type.Tystructor t, I in) {return t;}
	}
}
