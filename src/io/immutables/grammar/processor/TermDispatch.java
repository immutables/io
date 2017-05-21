package io.immutables.grammar.processor;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import io.immutables.collect.Vect;
import io.immutables.grammar.processor.CodepointMatch.When;
import io.immutables.grammar.processor.Grammars.Identifier;
import io.immutables.grammar.processor.Grammars.Literal;
import java.util.Map;
import java.util.Set;
import org.immutables.value.Value.Derived;
import org.immutables.value.Value.Enclosing;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Enclosing
@Immutable
interface TermDispatch {
	Vect<TermExpansion> terms();
	Vect<Selector> simple();
	Vect<Group> simpleGroups();
	Group complexGroup();

	default @Derived Map<Literal, TermExpansion> termsById() {
		return Maps.uniqueIndex(terms(), TermExpansion::id);
	}

	default @Derived Multimap<Identifier, TermExpansion> termsByKind() {
		return Multimaps.index(terms(), TermExpansion::kind);
	}

	@Immutable
	interface Selector {
		@Parameter
		Codepoint codepoint();
		@Parameter
		Group group();
	}

	@Immutable
	interface Group {
		@Parameter
		int index();
		@Parameter
		Set<TermExpansion> terms();
	}

	class Builder extends ImmutableTermDispatch.Builder {}

	static TermDispatch computeFrom(Vect<TermExpansion> terms) {
		TermDispatch.Builder builder =
				new TermDispatch.Builder()
						.addAllTerms(terms);

		Vect<TermExpansion> maybeSimple = terms
				.filter(d -> d.firstMatch().whenSimple() != When.NEVER);

		Map<Set<TermExpansion>, TermDispatch.Group> groups = Maps.newLinkedHashMap();

		for (Codepoint c : Codepoint.SIMPLE_SET) {
			Set<TermExpansion> matching = Sets.newLinkedHashSet();

			for (TermExpansion d : maybeSimple) {
				if (d.firstMatch().firstMatches(c)) {
					matching.add(d);
				}
			}

			TermDispatch.Group g = groups.computeIfAbsent(matching,
					m -> ImmutableTermDispatch.Group.of(groups.size(), m));

			builder.addSimple(ImmutableTermDispatch.Selector.of(c, g));
		}

		builder.addAllSimpleGroups(groups.values());
		builder.complexGroup(ImmutableTermDispatch.Group.of(
				groups.size(), // just to give some index
				terms.filter(d -> d.firstMatch().whenSimple() != When.ALWAYS)));

		return builder.build();
	}
}
