package io.immutables.grammar;

import org.immutables.value.Value;

/** Immutables style annotation for generated ast nodes. */
@Value.Style(
		add = "*",
		addAll = "*",
		attributelessSingleton = true,
		strictBuilder = true,
		overshadowImplementation = true,
		typeImmutable = "*Nodes",
		typeImmutableEnclosing = "*Nodes",
		visibility = Value.Style.ImplementationVisibility.PACKAGE,
		defaults = @Value.Immutable(builder = false, prehash = true))
@Value.Enclosing
public @interface Node {}
