package io.immutables.grammar.processor;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import io.immutables.Unreachable;
import io.immutables.collect.Vect;
import io.immutables.grammar.processor.Grammars.Alternative;
import io.immutables.grammar.processor.Grammars.AlternativeGroup;
import io.immutables.grammar.processor.Grammars.Cardinality;
import io.immutables.grammar.processor.Grammars.Group;
import io.immutables.grammar.processor.Grammars.Identifier;
import io.immutables.grammar.processor.Grammars.Literal;
import io.immutables.grammar.processor.Grammars.LiteralPart;
import io.immutables.grammar.processor.Grammars.MatchMode;
import io.immutables.grammar.processor.Grammars.ProductionPart;
import io.immutables.grammar.processor.Grammars.ReferencePart;
import io.immutables.grammar.processor.Grammars.SyntaxProduction;
import io.immutables.grammar.processor.Grammars.Unit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import static com.google.common.base.Preconditions.checkState;

/**
 * Production model prepared after analysis and suitable for deeper analysis or
 * code generation.
 */
@Enclosing
@Immutable
abstract class Production implements WithProduction {
	/** Production Id/type and it base for AST type names. */
	abstract Identifier id();

	/**
	 * Ephemeral is synthetic, intermediate inserted production indicator. It is used for AST
	 * insignificant productions and to expand groups inside production alternatives.
	 */
	abstract boolean ephemeral();

	/** One or more alternatives which production is comprised from. */
	abstract Vect<Alternative> alternatives();

	/** AST-significant (tagged) parts. */
	abstract Vect<TaggedPart> parts();

	/**
	 * AST production ids/types which are derived from alternatives, empty if having only 1
	 * alternative or if alternatives do not considered variant types, but just some syntactic
	 * deviations.
	 */
	abstract Vect<Identifier> subtypes();

	/**
	 * Supertypes are reverse binding of {@link #subtypes()}.
	 */
	abstract Vect<Identifier> supertypes();
	
	/**
	 * Alternatives contain references to the following ephemeral productions so that
	 * any tagged parts present in ephemeral productions can be accepted by this production's AST
	 * type. Think of it as supertypes to the AST object builder.
	 */
	abstract Vect<Identifier> ephemerals();

	/**
	 * All terminal subtypes: all unique non-abstract (terminal) subtypes including transitive
	 * subtypes of abstract subtypes.
	 */
	abstract Vect<Identifier> terminalSubtypes();

	/**
	 * Indicates if production has tagged parts, which, essentially, are AST object's attributes.
	 */
	boolean hasTagged() {
		return !parts().isEmpty();
	}

	@Lazy
	Vect<Alternative> alwaysSucceedingAlternatives() {
		return alternatives().filter(Production::isAlwaysSucceding);
	}

	static class Builder extends ImmutableProduction.Builder {}

	@Immutable
	interface TaggedPart {
		Identifier tag();
		Cardinality cardinality();
		Optional<Literal> literal();
		Optional<Identifier> reference();

		TaggedPart withCardinality(Cardinality cardinality);

		default @Check void check() {
			checkState(literal().isPresent() ^ reference().isPresent(),
					"Eithe literal or reference must be present " + this);
		}

		default boolean isCompatibleTo(TaggedPart part) {
			return (literal().isPresent() && part.literal().isPresent())
					|| (reference().isPresent() && part.reference().isPresent());
		}

		default TaggedPart span(TaggedPart part) {
			assert isCompatibleTo(part);
			Cardinality span = cardinality().span(part.cardinality());
			return withCardinality(span);
		}

		default TaggedPart append(TaggedPart part) {
			assert isCompatibleTo(part);
			Cardinality joined = cardinality().append(part.cardinality());
			return withCardinality(joined);
		}

		class Builder extends ImmutableProduction.TaggedPart.Builder {}
	}

	static Vect<Production> collectFrom(Unit unit) {
		Vect<Production> productions = extractProductions(unit);

		checkDuplicates(productions);
		checkMissingReferences(productions);
		checkPartInvariants(productions);
		checkLeftRecursion(productions);
		checkOnlySingleAlwaysSucceedAlterntive(productions);

		productions = rewriteGroupsToEphemeral(productions);
		productions = decorateWithTaggedParts(productions);
		productions = decorateWithTypes(productions);
		return productions;
	}

	private static void checkOnlySingleAlwaysSucceedAlterntive(Vect<Production> productions) {
		for (Production p : productions) {
			checkState(p.alwaysSucceedingAlternatives().size() <= 1,
					"Production '%' contains more that one always succeeding alternatives where everything is optional",
					p.id());
		}
	}

	private static boolean isAlwaysSucceding(Alternative a) {
		return a.parts().all(p -> p.mode() == MatchMode.CONSUME && p.cardinality().isZeroed());
	}

	private static Vect<Production> extractProductions(Unit unit) {
		Vect.Builder<Production> builder = Vect.builder();

		new GrammarsTransformer() {
			@Override
			public SyntaxProduction toSyntaxProduction(SyntaxProduction p) {
				builder.add(new Production.Builder()
						.id(p.name())
						.ephemeral(p.ephemeral())
						.addAllAlternatives(p.alternatives())
						.build());

				return p;
			}
		}.toUnit(unit);

		return builder.build();
	}

	private static Vect<Production> rewriteGroupsToEphemeral(Vect<Production> productions) {
		final Vect.Builder<Production> rewritten = Vect.builder();

		class Rewriter extends GrammarsTransformer {
			final Production production;
			int counter;

			Rewriter(Production production) {
				this.production = production;
			}

			Identifier newSyntheticId() {
				return Identifier.of(production.id().value() + "_" + (counter++));
			}

			@Override
			protected ProductionPart asProductionPart(AlternativeGroup group) {
				Identifier syntethicId = newSyntheticId();

				Vect<Alternative> alternatives = group.alternatives();

				if (group.tag().isPresent()) {
					// tag for each element just happens to be bundled with a group by parser
					// but will be distributed with each (single!) part of the each alternative
					Identifier tag = group.tag().get();

					alternatives = alternatives.map(
							a -> Alternative.of(a.parts().map(
									p -> (ProductionPart) p.withTag(tag))));
				}

				rewritten.add(new Production.Builder()
						.id(syntethicId)
						.ephemeral(true)
						.addAllAlternatives(alternatives)
						.build());

				return new ReferencePart.Builder()
						.reference(syntethicId)
						.cardinality(group.cardinality())
						.mode(group.mode())
						.build();
			}

			@Override
			protected ProductionPart asProductionPart(Group group) {
				Identifier syntethicId = newSyntheticId();

				rewritten.add(new Production.Builder()
						.id(syntethicId)
						.ephemeral(true)
						.addAlternatives(Alternative.of(group.parts()))
						.build());

				return new ReferencePart.Builder()
						.reference(syntethicId)
						.cardinality(group.cardinality())
						.mode(group.mode())
						.build();
			}
		}

		for (Production p : productions) {
			rewritten.add(
					p.withAlternatives(
							p.alternatives().map(
									new Rewriter(p)::toAlternative)));
		}

		return rewritten.build();
	}

	// expects tagged parts are already computed.
	private static Vect<Production> decorateWithTypes(Vect<Production> productions) {
		class Decorator {
			final Map<Identifier, Production> byIdentifier = Maps.uniqueIndex(productions, Production::id);
			final SetMultimap<Identifier, Identifier> subtypes = LinkedHashMultimap.create();
			final SetMultimap<Identifier, Identifier> supertypes = LinkedHashMultimap.create();
			final SetMultimap<Identifier, Identifier> ephemerals = LinkedHashMultimap.create();
			final SetMultimap<Identifier, Identifier> terminalSubtypes = LinkedHashMultimap.create();

			Vect<Production> decorate() {
				for (Production p : productions) {
					collectSubtypesAndEphemerals(p);
				}

				// Depends on collectSubtypesAndEphemerals for all productions
				// to be executed before
				for (Production p : productions) {
					collectLeafSubtypes(p.id(), p.id());
				}

				return productions.map(p -> p
						.withSubtypes(subtypes.get(p.id()))
						.withSupertypes(supertypes.get(p.id()))
						.withEphemerals(ephemerals.get(p.id()))
						.withTerminalSubtypes(terminalSubtypes.get(p.id())));
			}

			void collectSubtypesAndEphemerals(Production p) {
				if (!p.ephemeral()) {
					Vect<Identifier> choices = asIfChoiceReferences(p.alternatives());
					subtypes.putAll(p.id(), choices);
					for (Identifier t : choices) {
						supertypes.put(t, p.id());
					}
				}
				ephemerals.putAll(p.id(), asIfEphemeralReferences(p.alternatives()));
			}

			void collectLeafSubtypes(Identifier originSupertype, Identifier currentSupertype) {
				Set<Identifier> directSubtypes = subtypes.get(currentSupertype);
				if (directSubtypes.isEmpty()) {
					if (!originSupertype.equals(currentSupertype)) {
						terminalSubtypes.put(originSupertype, currentSupertype);
					}
				}
				for (Identifier s : directSubtypes) {
					collectLeafSubtypes(originSupertype, s);
				}
			}

			Vect<Identifier> asIfEphemeralReferences(Vect<Alternative> alternatives) {
				return alternatives.flatMap(Alternative::parts)
						.flatMap(p -> {
							if (p instanceof ReferencePart) {
								Production r = byIdentifier.get(((ReferencePart) p).reference());
								if (r.ephemeral()) return Vect.of(r);
							}
							return Vect.of();
						})
						.filter(r -> !r.parts().isEmpty())
						.map(Production::id);
			}

			Vect<Identifier> asIfChoiceReferences(Vect<Alternative> alternatives) {
				if (alternatives.size() == 1 && alternatives.get(0).singular()) {
					return Vect.of();
				}

				Predicate<Alternative> hasSingleReference =
						a -> a.parts()
								.<Boolean>when()
								.single(this::subtypingReference)
								.otherwise(false)
								.get();

				if (alternatives.all(hasSingleReference)) {
					return alternatives.map(
							a -> ((ReferencePart) a.parts().get(0)).reference());
				}

				return Vect.of();
			}

			boolean subtypingReference(ProductionPart p) {
				if (p instanceof ReferencePart) {
					Production r = byIdentifier.get(((ReferencePart) p).reference());
					return !r.ephemeral() && !p.tag().isPresent();
				}
				return false;
			}
		}

		return new Decorator().decorate();
	}

	private static Vect<Production> decorateWithTaggedParts(Vect<Production> productions) {
		Map<Identifier, Production> byIdentifier = Maps.uniqueIndex(productions, Production::id);

		class WithinCombiner {
			final Map<Identifier, TaggedPart> byId = new LinkedHashMap<>();

			void add(TaggedPart p) {
				byId.merge(p.tag(), p, TaggedPart::append);
			}

			Vect<TaggedPart> result() {
				return Vect.from(byId.values());
			}
		}

		class AcrossCombiner {
			final SetMultimap<Identifier, TaggedPart> byId = LinkedHashMultimap.create();
			int alternativeCount;

			void add(WithinCombiner combiner) {
				for (TaggedPart p : combiner.result()) {
					byId.put(p.tag(), p);
				}
				alternativeCount++;
			}

			Vect<TaggedPart> result() {
				Vect.Builder<TaggedPart> parts = Vect.builder();

				for (Collection<TaggedPart> v : byId.asMap().values()) {
					TaggedPart merged = Vect.from(v).reduce(TaggedPart::span);
					if (v.size() != alternativeCount) {
						// if tagged part is missing from some alternatives
						merged = merged.span(merged.withCardinality(Cardinality.C0_1));
					}
					parts.add(merged);
				}

				return parts.build();
			}
		}

		class PartsApplier {
			final Map<Identifier, Vect<TaggedPart>> taggedParts = new HashMap<>();

			Vect<TaggedPart> partsFor(Identifier type) {
				// computeIfAbsent for some reason gives strange NPE afterwards
				// therefore classsic caching idiom
				@Nullable Vect<TaggedPart> parts = taggedParts.get(type);
				if (parts == null) {
					parts = partsFrom(byIdentifier.get(type));
					taggedParts.put(type, parts);
				}
				return parts;
			}

			Vect<TaggedPart> partsFrom(Production production) {
				// do not collect tagged parts from subtype choice
				if (!production.subtypes().isEmpty()) return Vect.of();

				AcrossCombiner across = new AcrossCombiner();

				for (Alternative a : production.alternatives()) {
					WithinCombiner within = new WithinCombiner();

					for (ProductionPart part : a.parts()) {
						addPart(within::add, part);
						addReferencedParts(within::add, part);
					}

					across.add(within);
				}

				return across.result();
			}

			void addPart(Consumer<TaggedPart> parts, ProductionPart p) {
				if (!p.tag().isPresent() || p instanceof Group) return;

				TaggedPart.Builder b = new TaggedPart.Builder()
						.tag(p.tag().get())
						.cardinality(p.cardinality());

				if (p instanceof LiteralPart) {
					b.literal(((LiteralPart) p).literal());
				} else if (p instanceof ReferencePart) {
					b.reference(((ReferencePart) p).reference());
				} else {
					throw Unreachable.exhaustive();
				}

				parts.accept(b.build());
			}

			void addReferencedParts(Consumer<TaggedPart> parts, ProductionPart part) {
				if (!(part instanceof ReferencePart)) return;

				ReferencePart r = (ReferencePart) part;
				Production referenced = byIdentifier.get(r.reference());

				if (referenced.ephemeral()) {
					for (TaggedPart p : partsFor(referenced.id())) {
						Cardinality multiplied = r.cardinality().span(p.cardinality());
						parts.accept(p.withCardinality(multiplied));
					}
				}
			}

			Production withParts(Production p) {
				return p.withParts(partsFor(p.id()));
			}
		}

		return productions.map(new PartsApplier()::withParts);
	}

	private static void checkDuplicates(Vect<Production> productions) {
		Set<Identifier> redefinitions =
				Maps.filterEntries(
						Multimaps.index(productions, Production::id).asMap(),
						entry -> entry.getValue().size() > 1).keySet();

		checkState(redefinitions.isEmpty(),
				"Duplicate production definitions " + redefinitions);
	}

	private static void checkPartInvariants(Vect<Production> productions) {
		GrammarsTransformer checker = new GrammarsTransformer() {
			final Map<Identifier, Production> byIdentifier = Maps.uniqueIndex(productions, Production::id);

			void checkMatchMode(ProductionPart p) {
				checkState(p.mode() == MatchMode.CONSUME || !p.tag().isPresent(),
						"Tagged <&> and <!> are not supported " + p);
				checkState(p.mode() == MatchMode.CONSUME || p.cardinality().isExactlyOne(),
						"Not supporting <&> and <!> with cardinality that is not 1: " + p);
			}

			@Override
			protected ProductionPart asProductionPart(Group g) {
				checkState(!g.tag().isPresent(), "Not supporting tagged groups: " + g);
				return toGroup(g);
			}

			@Override
			protected ProductionPart asProductionPart(AlternativeGroup g) {
				// these checks are enforced by parser, but just to have preconditions
				checkState(g.alternatives().size() >= 2);
				for (Alternative a : g.alternatives()) {
					checkState(!a.singular());
					checkState(a.parts().size() == 1);
					checkState(!a.parts().get(0).tag().isPresent());
				}
				return toAlternativeGroup(g);
			}

			@Override
			protected ProductionPart asProductionPart(LiteralPart l) {
				checkMatchMode(l);
				return l;
			}

			@Override
			protected ProductionPart asProductionPart(ReferencePart r) {
				checkMatchMode(r);
				checkState(!r.tag().isPresent() || !byIdentifier.get(r.reference()).ephemeral(),
						"Ephemeral references cannot be tagged: " + r.reference());

				return r;
			}
		};

		productions.flatMap(Production::alternatives)
				.forEach(checker::toAlternative);
	}

	private static void checkMissingReferences(Vect<Production> productions) {
		Map<Identifier, Production> byIdentifier = Maps.uniqueIndex(productions, Production::id);
		final Set<Identifier> missingReferences = new LinkedHashSet<>();

		GrammarsTransformer detector = new GrammarsTransformer() {
			@Override
			protected ProductionPart asProductionPart(ReferencePart value) {
				Identifier reference = value.reference();
				if (!byIdentifier.containsKey(reference)) {
					missingReferences.add(reference);
				}
				return value;
			}
		};

		for (Production production : productions) {
			production.alternatives().forEach(detector::toAlternative);
		}

		checkState(missingReferences.isEmpty(),
				"Referenced productions are missing " + missingReferences);
	}

	private static void checkLeftRecursion(Vect<Production> productions) {
		Map<Identifier, Production> byIdentifier = Maps.uniqueIndex(productions, Production::id);

		final Set<Identifier> recurseOffenders = new LinkedHashSet<>();

		class Detector extends GrammarsTransformer {
			final Set<Identifier> scanned = new HashSet<>();
			final Production origin;

			Detector(Production origin) {
				this.origin = origin;
			}

			void scan(Production production) {
				production.alternatives().forEach(this::toAlternative);
			}

			@Override
			public Alternative toAlternative(Alternative value) {
				ProductionPart leftmost = value.parts().get(0);
				asAlternativeParts(value, leftmost);
				return value;
			}

			@Override
			public Group toGroup(Group value) {
				ProductionPart leftmost = value.parts().get(0);
				asGroupParts(value, leftmost);
				return value;
			}

			@Override
			protected ProductionPart asProductionPart(ReferencePart value) {
				Identifier reference = value.reference();

				if (!scanned.add(reference)) {
					return value;
				}

				if (origin.id().equals(reference)) {
					recurseOffenders.add(origin.id());
					return value;
				}

				scan(byIdentifier.get(reference));
				return value;
			}
		}

		for (Production production : productions) {
			new Detector(production).scan(production);
		}

		checkState(recurseOffenders.isEmpty(),
				"Productions have leftmost recursion and cannot terminate " + recurseOffenders);
	}
}
