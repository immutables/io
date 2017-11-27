package io.immutables.grammar.processor;

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import io.immutables.collect.Vect;
import io.immutables.grammar.processor.Grammars.Alternative;
import io.immutables.grammar.processor.Grammars.Cardinality;
import io.immutables.grammar.processor.Grammars.Identifier;
import io.immutables.grammar.processor.Grammars.LiteralPart;
import io.immutables.grammar.processor.Grammars.MatchMode;
import io.immutables.grammar.processor.Production.TaggedPart;
import javax.annotation.Nullable;
import org.immutables.generator.AbstractTemplate;
import org.immutables.generator.Generator.Template;
import org.immutables.generator.Generator.Typedef;
import org.immutables.generator.Templates;

@Template
abstract class Generator extends AbstractTemplate {
	abstract Templates.Invokable generate();

	@Typedef
	CodepointMatch Match;

	@Typedef
	TermExpansion Term;

	@Typedef
	Production Prod;

	@Typedef
	Alternative Alt;

	@Typedef
	PartCase Part;

	@Typedef
	TaggedPart Tagged;

	final Identifier unkinded = TermExpansion.KIND_DEFAULT;

	String pack;
	String name;
	TermDispatch dispatch;
	Vect<Production> productions;

	private ImmutableMap<Identifier, Production> byIdentifier;

	ImmutableSet<Identifier> uniqueParts;

	Templates.Invokable with(String pack, String name, TermDispatch dispatch, Vect<Production> productions) {
		this.pack = pack;
		this.name = name;
		this.dispatch = dispatch;
		this.productions = productions;
		this.byIdentifier = Maps.uniqueIndex(productions, Production::id);
		this.uniqueParts = FluentIterable.from(productions)
				.transformAndConcat(Production::parts)
				.transform(TaggedPart::tag)
				.toSet();

		return generate();
	}

	final int one = 1;
	final Predicate<Integer> mod16 = i -> i % 16 == 0;

	final Function<Object, String> asVar =
			Functions.compose(
					CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_CAMEL),
					Functions.toStringFunction());

	final Function<Object, String> asType =
			Functions.compose(
					CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.UPPER_CAMEL),
					Functions.toStringFunction());

	final Function<Object, String> asConstant =
			Functions.compose(
					CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.UPPER_UNDERSCORE),
					Functions.toStringFunction());

	final Function<Grammars.Literal, String> asTokenConstant =
			id -> asConstant.apply(dispatch.termsById().get(id).name().value());

	final Function<Grammars.ProductionPart, PartCase> asPartCase = PartCase::new;
	
	class PartCase {
		final Cardinality cardinality;
		final boolean consume;
		final boolean not;
		final boolean and;
		final boolean tagged;
		final String var;
		final String tag;
		final @Nullable Grammars.Literal literal;
		final @Nullable Production reference;

		PartCase(Grammars.ProductionPart part) {
			this.cardinality = part.cardinality();

			this.consume = part.mode() == MatchMode.CONSUME;
			this.and = part.mode() == MatchMode.AND;
			this.not = part.mode() == MatchMode.NOT;

			this.tagged = part.tag().isPresent();
			this.tag = part.tag().map(Object::toString).orElse("");
			this.var = asVar.apply(tag);

			this.literal = part instanceof LiteralPart
					? ((LiteralPart) part).literal()
					: null;

			this.reference = part instanceof Grammars.ReferencePart
					? byIdentifier.get(((Grammars.ReferencePart) part).reference())
					: null;
		}
	}
}
