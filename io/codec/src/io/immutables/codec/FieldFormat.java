package io.immutables.codec;

import java.lang.annotation.*;
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
