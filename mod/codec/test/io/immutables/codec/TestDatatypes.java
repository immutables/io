package io.immutables.codec;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import io.immutables.codec.Datatype.Builder;
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

	static <C> TypeToken<Generi<C>> forGenericArgument(TypeToken<C> argument) {
		return new TypeToken<Generi<C>>() {}
				.where(new TypeParameter<C>() {}, argument);
	}

	@Test
	public void structSet() {
		Builder<Struct> builder = datatype.builder();
		datatype.setter("a", int.class).set(builder, 10);
		datatype.setter("b", String.class).set(builder, "bb");
		Struct struct = builder.build();

		that(struct.a).is(10);
		that(struct.b).is("bb");
	}

	@Test
	public void structGet() {
		Struct struct = new Struct();
		struct.a = 44;
		struct.b = "abc";

		Integer a = datatype.getter("a", int.class).get(struct);
		String b = datatype.getter("b", String.class).get(struct);

		that(a).is(44);
		that(b).is("abc");
	}

	@Test
	public void genericSetGet() {
		Builder<Generi<String>> builder = genericDatatype.builder();
		genericDatatype.setter("c", String.class).set(builder, "abcd");
		String s = genericDatatype.getter("c", String.class).get(builder.build());

		that(s).is("abcd");
	}
}
