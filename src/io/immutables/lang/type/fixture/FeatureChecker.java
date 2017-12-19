package io.immutables.lang.type.fixture;

import io.immutables.lang.type.Type;
import io.immutables.lang.type.Type.Feature;

class FeatureChecker extends TypeMatcher {
	private final Feature feature;

	FeatureChecker(Type.Feature feature) {
		super(feature.parameters());
		this.feature = feature;
	}
//
//	Node.ApplyFeature check(SyntaxTrees.Argument input) {
//
//	}
//
//	Node.ApplyFeature check(SyntaxTrees.Argument input, Type expected) {
//		if (match(expected, feature.out())) {
//			return Node.ApplyFeature.
//		}
//		if (output.)
//
//
//	}
}
