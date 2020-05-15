package io.immutables.grammar;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import io.immutables.Source;
import io.immutables.grammar.Productions.Traversal;
import io.immutables.grammar.Productions.Traversal.At;
import io.immutables.grammar.fixture.ExprProductions;
import io.immutables.grammar.fixture.ExprTerms;
import io.immutables.grammar.fixture.ExprTrees.Expressions;

public class MainFixture {
	public static void main1() {
		String input = Strings.repeat("1 + \n", 10000) + " [2 ___!,3,vvgg ] ";

		// String input = "\n[1\n, 2,\n,3, 4\n, 5, 6, 7,\n n,\n__!] \n\n\n";
		// String input = "\n[1__\n] ";
		// String input = "1__] ";
		ExprTerms terms = ExprTerms.from(input.toCharArray());
		terms.ok();
		Source.Range range = terms.firstUnexpectedRange();
		System.out.println(range);
		System.out.println(terms.highlight(range));
	}

	public static void main(String... args) {
		String input = "1 + [2, a, b , 3] + [c, 1]";
		ExprTerms terms = ExprTerms.from(input.toCharArray());
		if (!terms.ok()) {
			Source.Range range = terms.firstUnexpectedRange();
			System.out.println(range);
			System.out.println(terms.highlight(range));
			return;
		}
		ExprProductions<Expressions> expressions = ExprProductions.expressions(terms);
		if (!expressions.ok()) {
			System.out.println(expressions.message());
			return;
		}
		System.out.println(expressions.show());
		Traversal t = expressions.traverse();
		At at;
		int ind = 0;
		while ((at = t.next()) != At.EOP) {
			if (at != At.PRODUCTION_END) {
				System.out.println(Strings.padEnd(Strings.repeat("  ", ind) + "*", 10, ' ') + t.show());
			}
			if (at == At.PRODUCTION_BEGIN) ind++;
			if (at == At.PRODUCTION_END) ind--;
			System.out.println(terms.highlight(t.range()));
		}
		t = expressions.traverse();

		HashMultiset<Object> multiset = HashMultiset.create();
		while ((at = t.next()) != At.EOP) {
			multiset.add(at);
		}

		System.out.println(multiset);
	}
}
