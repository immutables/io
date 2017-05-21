package io.immutables.grammar.processor;

import io.immutables.grammar.processor.Grammars.Cardinal;
import io.immutables.grammar.processor.Grammars.Cardinality;
import io.immutables.grammar.processor.Grammars.Identifier;
import io.immutables.grammar.processor.Grammars.MatchMode;
import io.immutables.grammar.processor.Grammars.Matched;
import io.immutables.grammar.processor.Grammars.Tagged;
import org.parboiled.Action;
import org.parboiled.Context;
import org.parboiled.support.ValueStack;

final class ParserActions {
	private ParserActions() {}

	static Action<Object> with(Cardinality cardinality) {
		return new Action<Object>() {
			@Override
			public boolean run(Context<Object> context) {
				ValueStack<Object> stack = context.getValueStack();
				Cardinal part = (Cardinal) stack.peek();
				part = part.withCardinality(cardinality);
				stack.poke(part);
				return true;
			}
		};
	}

	static Action<Object> with(MatchMode mode) {
		return new Action<Object>() {
			@Override
			public boolean run(Context<Object> context) {
				ValueStack<Object> stack = context.getValueStack();
				Matched part = (Matched) stack.peek();
				part = part.withMode(mode);
				stack.poke(part);
				return true;
			}
		};
	}

	static Action<Object> tagged() {
		return new Action<Object>() {
			@Override
			public boolean run(Context<Object> context) {
				ValueStack<Object> stack = context.getValueStack();
				if (stack.size() >= 2
						&& stack.peek(0) instanceof Tagged
						&& stack.peek(1) instanceof Identifier) {

					Tagged tagged = (Tagged) stack.pop();
					Identifier identifier = (Identifier) stack.pop();
					tagged = tagged.withTag(identifier);
					stack.push(tagged);
				}
				return true;
			}
		};
	}
}
