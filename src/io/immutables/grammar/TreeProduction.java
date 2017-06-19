package io.immutables.grammar;

import org.immutables.value.Value.Auxiliary;

/**
 * Syntax tree production, usually used for abstract syntax trees.
 * @param <K> ast kind type used to for typesafe guarding, usually it's an umbrella top level type
 *          of generated nested ast classes.
 */
public interface TreeProduction<K> {
	abstract @Auxiliary int termBegin();
	abstract @Auxiliary int termEnd();
	//abstract @Auxiliary int productionIndex();

	interface Builder {}
}
