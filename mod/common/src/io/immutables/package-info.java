@Value.Style(
		strictBuilder = true,
		overshadowImplementation = true,
		visibility = Value.Style.ImplementationVisibility.PACKAGE,
		defaults = @Value.Immutable(builder = false))
package io.immutables;

import org.immutables.value.Value;
