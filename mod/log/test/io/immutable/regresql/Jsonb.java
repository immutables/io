package io.immutable.regresql;

import io.immutables.codec.CodecQualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maybe it's only temporary here.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@CodecQualifier
@interface Jsonb {

	public static final class Impl {
		private static final Jsonb JSONB;
		static {
			try {
				JSONB = Impl.class.getMethod("jsonb").getAnnotation(Jsonb.class);
			} catch (NoSuchMethodException | SecurityException ex) {
				throw new AssertionError(ex);
			}
		}

		public static @Jsonb Jsonb jsonb() {
			return JSONB;
		}
	}
}
