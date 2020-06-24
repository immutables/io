package io.immutables.codec;

import io.immutables.Unreachable;
import io.immutables.codec.Codec.At;
import io.immutables.codec.Codec.FieldIndex;
import io.immutables.codec.Codec.In;
import io.immutables.codec.Codec.Out;
import java.io.IOException;

public class Pipe {
	private Pipe() {}

	public static void onValue(In in, Out out) throws IOException {
		At t = in.peek();
		switch (t) { // @formatter:off
		case NULL: in.takeNull(); out.putNull(); break;
		case INT: out.putInt(in.takeInt()); break;
		case LONG: out.putLong(in.takeLong()); break;
		case DOUBLE: out.putDouble(in.takeDouble()); break;
		case BOOLEAN: out.putBoolean(in.takeBoolean()); break;
		case STRING: out.putString(in.takeString()); break;
		case STRUCT: onStruct(out, in); break;
		case ARRAY: onArray(out, in); break;
		case FIELD: //$FALL-THROUGH$
		case STRUCT_END: //$FALL-THROUGH$
		case ARRAY_END: //$FALL-THROUGH$
		case EOF: throw Unreachable.contractual();
		}	// @formatter:on
	}

	public static void onStruct(Out out, In in) throws IOException {
		FieldIndex mapper = Codec.arbitraryFields();
		in.beginStruct(mapper);
		out.beginStruct(mapper);
		while (in.hasNext()) {
			out.putField(in.takeField());
			onValue(in, out);
		}
		out.endStruct();
		in.endStruct();
	}

	public static void onArray(Out out, In in) throws IOException {
		in.beginArray();
		out.beginArray();
		while (in.hasNext()) {
			onValue(in, out);
		}
		out.endArray();
		in.endArray();
	}
}
