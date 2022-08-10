package io.immutables.lang.node;

interface BuiltinTypes {
	Typed Empty = Types.empty();
	Typed i32 = Tyctor.basic("i32").instance();
	Typed u32 = Tyctor.basic("u32").instance();
	Typed f32 = Tyctor.basic("f32").instance();
	Typed Str = Tyctor.basic("str").instance();
}
