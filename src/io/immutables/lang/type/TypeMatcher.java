package io.immutables.lang.type;

interface Context {
	boolean matches();
	boolean satisfied();
	Context argument(Type.Variable variable, Type type);
	boolean isParameter(Type.Variable variable);
	int parameterCount();
	Type.Variable parameterAt(int index);
	Type argumentAt(Type type);

}

public class TypeMatcher implements Type.Visitor<Type, Context> {
	private final Context context;

	private TypeMatcher(Context context) {
		this.context = context;
	}

}
