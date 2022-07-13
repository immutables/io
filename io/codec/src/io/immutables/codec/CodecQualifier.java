package io.immutables.codec;

import java.lang.annotation.*;

/**
 * As we support matching only a single qualifier annotation, we use this meta-annotation as a marker
 * of which one annotation to use to qualify codec.
 */
@Documented
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodecQualifier {}
