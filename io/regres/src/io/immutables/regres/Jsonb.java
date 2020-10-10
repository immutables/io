package io.immutables.regres;

import io.immutables.codec.CodecQualifier;
import org.immutables.value.Value;
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
@Value.Immutable(singleton = true)
// , builder = false <-- TODO fix immutables, this produces wrong code
public @interface Jsonb {}
