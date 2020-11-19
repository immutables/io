package io.immutables.micro;

import com.google.inject.Module;

/**
 * Servicelets as a modules are contributed to multibinding (as {@code Set<ServiceletModule>}) inside platform so can be
 * handled in a special way.
 * @see MixinModule
 */
public interface ServiceletModule extends Module {}
