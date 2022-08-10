package io.immutables.lang.type;

import io.immutables.collect.Vect;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class TypeIf {
	static boolean Variable(Type type) {
		return type instanceof Type.Variable;
	}

	static boolean Variable(Type type, Consumer<Type.Variable> consumer) {
		if (type instanceof Type.Variable) {
			consumer.accept((Type.Variable) type);
			return true;
		}
		return false;
	}

	static boolean Terminal(Type type, Consumer<Type.Terminal> consumer) {
		if (type instanceof Type.Terminal) {
			consumer.accept((Type.Terminal) type);
			return true;
		}
		return false;
	}

	static boolean Applied(Type type, Consumer<Type.Applied> consumer) {
		if (type instanceof Type.Applied) {
			consumer.accept((Type.Applied) type);
			return true;
		}
		return false;
	}

	static boolean Parameter(Type type, Consumer<Type.Parameter> consumer) {
		if (type instanceof Type.Parameter) {
			consumer.accept((Type.Parameter) type);
			return true;
		}
		return false;
	}

	static boolean Product(Type type, Consumer<Type.Product> consumer) {
		if (type instanceof Type.Product) {
			consumer.accept((Type.Product) type);
			return true;
		}
		return false;
	}

	static boolean Empty(Type type, Runnable consumer) {
		if (type == Type.Product.Empty) {
			consumer.run();
			return true;
		}
		return false;
	}

	static boolean Nominal(Type type, Consumer<Type.Nominal> consumer) {
		if (type instanceof Type.Nominal) {
			consumer.accept((Type.Nominal) type);
			return true;
		}
		return false;
	}

	static boolean Structural(Type type, Consumer<Type.Structural> consumer) {
		if (type instanceof Type.Structural) {
			consumer.accept((Type.Structural) type);
			return true;
		}
		return false;
	}

	static boolean pairwise(Vect<Type> t1, Vect<Type> t2, BiConsumer<Type, Type> consumer) {
		if (t1.size() == t2.size()) {
			for (var i = 0; i < t1.size(); i++) {
				consumer.accept(t1.get(i), t2.get(i));
			}
			return true;
		}
		return false;
	}
}
