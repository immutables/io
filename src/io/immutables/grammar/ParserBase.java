package io.immutables.grammar;

import java.util.NoSuchElementException;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public abstract class ParserBase {
	private final Terms terms;
	private int mismatchedTokenIndex = -1;
	private AstProduction.Id mismatchedProduction;
	private AstProduction.Id production;
	private int mismatchedExpectedToken = Terms.UNEXPECTED;
	private int mismatchedActualToken = Terms.UNEXPECTED;

	public ParserBase(Terms terms) {
		this.terms = terms;
	}

	protected void production(AstProduction.Id production) {
		this.production = production;
	}

	protected final void mismatchAt(int tokenIndex, int expectedToken, int actualToken) {
		if (tokenIndex > mismatchedTokenIndex) {
			this.mismatchedTokenIndex = tokenIndex;
			this.mismatchedProduction = production;
			this.mismatchedExpectedToken = expectedToken;
			this.mismatchedActualToken = actualToken;
		}
	}

	public String getMismatchedExpectedToken() {
		if (!hasMismatchedToken()) throw new NoSuchElementException();
		return terms.showTerm(mismatchedExpectedToken);
	}

	public String getMismatchedActualToken() {
		if (!hasMismatchedToken()) throw new NoSuchElementException();
		return terms.showTerm(mismatchedActualToken);
	}

	public AstProduction.Id getFarthestMismatchedProduction() {
		if (!hasMismatchedToken()) throw new NoSuchElementException();
		return mismatchedProduction;
	}

	public boolean hasMismatchedToken() {
		return mismatchedTokenIndex >= 0;
	}

	public Source.Range getFarthestMismatchedToken() {
		if (!hasMismatchedToken()) throw new NoSuchElementException();
		return terms.range(mismatchedTokenIndex);
	}
}
