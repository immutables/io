package io.immutables.codec;

import org.immutables.data.Data;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Data
@Enclosing
public interface Cases {
	@Immutable
	interface A extends Cases {
		int a();
		class Builder extends ImmutableCases.A.Builder {}
	}
	@Immutable
	interface B extends Cases {
		boolean b();
		class Builder extends ImmutableCases.B.Builder {}
	}
	@Immutable(builder = false)
	interface C extends Cases {
		@Parameter
		String c();
		static C of(String str) {
			return ImmutableCases.C.of(str);
		}
	}
	@Immutable(builder = false, singleton = true)
	interface D extends Cases {
		static D of() {
			return ImmutableCases.D.of();
		}
	}
}
