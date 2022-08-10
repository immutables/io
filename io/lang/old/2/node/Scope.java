package io.immutables.lang.node;

interface Scope {
	FeatureArrow feature(Typed on, String name);
	FeatureArrow feature(String name);
}
