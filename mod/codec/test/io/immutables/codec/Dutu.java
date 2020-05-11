package io.immutables.codec;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import org.immutables.data.Data;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Immutable
@Data
@Enclosing
public interface Dutu {
	int i();
	@Nullable
	Double d();
	String s();

	class Builder extends ImmutableDutu.Builder {}

	@Immutable(builder = false)
	interface Bubu<B> {
		@Parameter
		B b();

		static <B> Bubu<B> of(B b) {
			return ImmutableDutu.Bubu.of(b);
		}
	}

	@Immutable
	interface Opts {
		Optional<String> s();
		OptionalInt i();
		OptionalLong l();
		OptionalDouble d();

		class Builder extends ImmutableDutu.Opts.Builder {}
	}
}
