package io.immutables.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SourceRun {
	/** source pattern (glob) to compile and run source files, like "*.ext" */
	String value();
}
