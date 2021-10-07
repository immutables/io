package io.immutables.regres;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface SqlAccessor {
	/**
	 * Applicable to int, long, int[], long[]. When non-array form is used, the returned cound will be
	 * the sum of all update counts where multiple update counts could be returned for either multiple
	 * statements in snipped or when using batch statement execution.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface UpdateCount {}

	/** Expects single row to bind to result. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface Single {
		/**
		 * Marks support for 0..1 rows result. It's hard to reliably detect in multi-language and
		 * libraries environment (Java, Kotlin etc) the type for nullable or optional wrappers, so we
		 * explicitly declare that zero rows is not an error and we can bind to any type supporting
		 * converting from either value or {@code null} (for zero rows).
		 */
		boolean optional() default false;
		/**
		 * Would take first result row and ignore the rest of records. If this is {@code false} (the
		 * default) it would be an error to receive more than one record.
		 */
		boolean ignoreMore() default false;
	}

	/**
	 * Use this annotation when extracting only single column, not the entire result set row.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface Column {
		/**
		 * Column name (column label in result set) to extract. When not empty, this takes precedence
		 * over {@link #index()}, which defaults to the first (0) column.
		 */
		String value() default "";
		/**
		 * Column index to extract. Zero-based, not one-based. By default, it is the first column (0).
		 */
		int index() default 0;
	}

	/**
	 * Mark parameter to be used for batching. Other parameters will be reused for each batch entry,
	 * but this one will be iterated. The parameter should be Iterable or an array.
	 * The presence of this parameter will enable batch execution mode. Result sets are not supported
	 * for batch scripts, only update counts are processed.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Batch {}

	/**
	 * This can be used to specify parameter name. Under better circumstances those could be returned
	 * by reflection, but this requires code to be compiled with '-parameters' flag for javac.
	 * Otherwise all params would have synthetic names like 'arg0', 'arg1' etc. At this point it is prohibited
   * to not have parameters named via this annotations (unless it is Spread)
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Named {
		String value();
	}

	/**
	 * This parameter object will be spread as if it was marshaled to the attributes, and those individual
	 * attributes will constitute placeholders in SQL.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface Spread {
		/** This prefix will be added to each attribute placeholder of an object. */
		String prefix() default "";
	}

	ConnectionProvider.Handle connectionHandle();
}
