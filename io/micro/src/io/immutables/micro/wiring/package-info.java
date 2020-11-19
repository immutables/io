/**
 * {@code wiring} package contains implementation modules for different technologies comprising micro platform. The
 * intention is that only infrastructure parts should access this package, even DSL should actually avoid using these
 * implementation modules in favour of using technology specific support interfaces/factories in {@code micro}. More
 * specialized and decoupled modules could be found elsewhere, like things for testing or completely additive and
 * decoupled technology modules. The modules here are either essential for platform features to work together or too
 * premature to be moved into technology specific packages or corresponding codebase modules.
 * <p>
 * <em>Alternative package name could have been one of "platform", "modules", "support", "impl", but
 * as you see all those are very stereotypical, either too clichéd, too overloaded or containing "layer" or kind/role
 * designation rather than feature designation. Here we express that these modules compise "wiring" necessary for
 * micro-platform to together.</em>
 */
@javax.annotation.ParametersAreNonnullByDefault
package io.immutables.micro.wiring;
