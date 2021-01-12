package io.immutables.codec;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.google.common.base.CaseFormat;

/**
 * As we support matching only a single qualifier annotation, we use this meta-annotation as a marker
 * of which one annotation to use to qualify codec.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldFormat {
	CaseFormat value();
}
