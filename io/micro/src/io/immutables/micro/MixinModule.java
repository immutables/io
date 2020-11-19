package io.immutables.micro;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

/**
 * Superinterface for mixin modules, i.e. these modules are contributed by platform modules (via {@link
 * Multibinder}/{@link ProvidesIntoSet}) and are added to each servicelet running on the platform. Note: For some reason
 * using just {@link Module} with qualifiers doesn't work, Guice internals apply some special meaning to those and we
 * cannot do multibinding for {@code Set<Module>}. But if we use subinterface {@link MixinModule}, Guice works as
 * expected.
 */
public interface MixinModule extends Module {}
