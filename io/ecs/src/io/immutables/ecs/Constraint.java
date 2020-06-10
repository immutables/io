package io.immutables.ecs;

import org.immutables.data.*;
import org.immutables.value.Value.*;
import static com.google.common.base.Preconditions.*;

@Data
@Enclosing
public interface Constraint {

	@Immutable
	abstract class Concept implements Constraint {
		public abstract @Parameter Type reference();

		@Check void check() {
			var r = reference();
			checkState(r instanceof Type.Reference || r instanceof Type.Parameterized);
		}

		public static Concept of(Type t) {
			return ImmutableConstraint.Concept.of(t);
		}
	}
}
