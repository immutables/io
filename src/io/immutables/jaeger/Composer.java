package io.immutables.jaeger;

import io.immutables.jaeger.Swagger.InfoObject;
import io.immutables.jaeger.Swagger.RootObject;
import io.immutables.jaeger.Swagger.Scheme;
import io.immutables.jaeger.parse.JaegerTrees.Unit;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

final class Composer {
	final Unit unit;
	private final RootObject.Builder root = new RootObject.Builder();
	private final InfoObject.Builder info = new InfoObject.Builder();
	private final Map<Tag, String> tags = new LinkedHashMap<>();
	private final StringBuilder comments = new StringBuilder();

	Composer(Unit unit) {
		this.unit = unit;
	}

	void process() {
//		Vect<UnitElement> elements = unit.elements();
//
//		boolean wasLocalBinding = false;
//		for (UnitElement e : elements) {
//			if (e instanceof JaegerTrees.Empty) {
//				((JaegerTrees.Empty) e).comment()
//						.ifPresent(s -> appendComment(comments, tags, s));
//				continue;
//			}
//			if (e instanceof JaegerTrees.LocalBinding) {
//				if (!wasLocalBinding) {
//					info.description(comments.toString());
//				}
//				wasLocalBinding = true;
//				JaegerTrees.LocalBinding binding = (JaegerTrees.LocalBinding) e;
//				comments.toString();
//
//				JaegerTrees.ExpressionLiteral value = binding.from();
//				if (value instanceof JaegerTrees.LiteralString) {
//					LiteralString literalString = (JaegerTrees.LiteralString) value;
//					assignAttribute(binding.to().toString(), literalString.literal().unquote());
//				} else if (value instanceof JaegerTrees.LiteralSequence) {
//					LiteralSequence literalSequence = (JaegerTrees.LiteralSequence) value;
//					for (ExpressionLiteral component : literalSequence.component()) {
//						if (component instanceof JaegerTrees.LiteralString) {
//							LiteralString literalString = (JaegerTrees.LiteralString) component;
//							assignAttribute(binding.to().toString(), literalString.literal().unquote());
//						}
//					}
//				}
//			}
//			if (e instanceof JaegerTrees.RestEndpoint) {
//
//			}
//			if (e instanceof JaegerTrees.TypeDeclaration) {
//				TypeDeclaration typeDeclaration = (JaegerTrees.TypeDeclaration) e;
//				addDefinition(typeDeclaration);
//			}
//			tags.clear();
//			comments.setLength(0);
//		}
//	}
//
//	private void addDefinition(TypeDeclaration typeDeclaration) {
//		SchemaObject.Builder builder = new SchemaObject.Builder();
//
//		String name = typeDeclaration.name().toString();
//		Optional<Features> features = typeDeclaration.features();
//		if (features.isPresent()) {
//			for (NamedParametersBind featureNamed : features.get().element()) {
//				String featureName = featureNamed.name().toString();
//				TypeReference type = featureNamed.type();
//				if (type instanceof JaegerTrees.TypeReferenceDeclared) {
//					boolean present = ((JaegerTrees.TypeReferenceDeclared) type).optional().isPresent();
//				} else {
//
//				}
//				builder.putProperties(featureName, null);
//			}
//		}
//
//		String type = tags.get(Tag.TYPE);
//
//		root.putDefinitions(name, builder.build());
	}

	private void assignAttribute(String name, String value) {
		switch (name) {
		case "base":
			root.basePath(value);
			break;

		case "host":
			root.host(URI.create(value));
			break;

		case "title":
			info.title(value);
			break;

		case "version":
			info.version(value);
			break;

		case "produces":
			root.addProduces(value);
			break;

		case "consumes":
			root.addConsumes(value);
			break;

		case "schemes":
			root.addSchemes(Scheme.valueOf(value));
			break;

		default:
			break;
		}
	}

	RootObject compose() {
		return root
				.info(info.build())
				.build();
	}
//
//	private void appendComment(StringBuilder comments, Map<Tag, String> tags, Symbol symbol) {
//		CharSequence comment = symbol.subSequence("//".length(), symbol.length());
//		Optional<Entry<Tag, String>> tag = Tag.detect(comment);
//		if (tag.isPresent()) {
//			tag.ifPresent(t -> tags.put(t.getKey(), t.getValue()));
//		} else {
//			comments.append(comment);
//		}
//	}
}
