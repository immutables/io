package io.immutables.lang.type;

interface Context {
	boolean matches();
	boolean satisfied();
	Context argument(Type22.Variable variable, Type22 type);
	boolean isParameter(Type22.Variable variable);
	int parameterCount();
	Type22.Variable parameterAt(int index);
	Type22 argumentAt(Type22 type);

}

public class TypeMatcher implements Type22.Visitor<Type22, Context> {
	private final Context context;

	private TypeMatcher(Context context) {
		this.context = context;
	}

}
