package io.immutables.codec;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import org.immutables.data.Datatype;
import org.immutables.data.Datatype.Builder;
import org.immutables.data.Datatype.Feature;
import org.junit.Test;
import static io.immutables.that.Assert.that;

public class TestDatatypes {

	public static class Struct {
		public int a;
		public String b;
	}

	public static class Generi<C> {
		public C c;
	}

	final Datatype<Struct> datatype = Datatypes.forStruct(Struct.class);
	final Datatype<Generi<String>> genericDatatype = Datatypes.forStruct(forGenericArgument(TypeToken.of(String.class)));
	final Feature<Struct, Integer> a_ = datatype.feature("a", TypeToken.of(int.class));
	final Feature<Struct, String> b_ = datatype.feature("b", TypeToken.of(String.class));
	final Feature<Generi<String>, String> c_ = genericDatatype.feature("c", TypeToken.of(String.class));

	static <C> TypeToken<Generi<C>> forGenericArgument(TypeToken<C> argument) {
		return new TypeToken<Generi<C>>() {}
				.where(new TypeParameter<C>() {}, argument);
	}

	@Test
	public void structSet() {
		Builder<Struct> builder = datatype.builder();
		builder.set(a_, 10);
		builder.set(b_, "bb");
		Struct struct = builder.build();

		that(struct.a).is(10);
		that(struct.b).is("bb");
	}

	@Test
	public void structGet() {
		Struct struct = new Struct();
		struct.a = 44;
		struct.b = "abc";

		Integer a = datatype.get(a_, struct);
		String b = datatype.get(b_, struct);

		that(a).is(44);
		that(b).is("abc");
	}

	@Test
	public void genericSetGet() {
		Builder<Generi<String>> builder = genericDatatype.builder();
		builder.set(c_, "abcd");

		that(genericDatatype.get(c_, builder.build())).is("abcd");
	}
}
