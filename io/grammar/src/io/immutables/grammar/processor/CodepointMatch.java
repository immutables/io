package io.immutables.grammar.processor;

import com.google.common.base.Joiner;
import com.google.common.collect.BoundType;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;
import io.immutables.Unreachable;
import io.immutables.collect.Vect;
import java.util.Optional;
import javax.annotation.Nullable;
import static com.google.common.base.Preconditions.checkState;

abstract class CodepointMatch {

	enum When {
		ALWAYS, MAYBE, NEVER;
	}

	abstract When whenSimple();

	abstract boolean firstMatches(Codepoint point);

	@Nullable
	Equal equal() {
		return this instanceof Equal ? (Equal) this : null;
	}

	@Nullable
	NotEqual notEqual() {
		return this instanceof NotEqual ? (NotEqual) this : null;
	}

	@Nullable
	InRange inRange() {
		return this instanceof InRange ? (InRange) this : null;
	}

	@Nullable
	NotInRange notInRange() {
		return this instanceof NotInRange ? (NotInRange) this : null;
	}

	@Nullable
	SmallTable smallTable() {
		return this instanceof SmallTable ? (SmallTable) this : null;
	}

	@Nullable
	CharMatches charMatches() {
		return this instanceof CharMatches ? (CharMatches) this : null;
	}

	@Nullable
	Sequence sequence() {
		return this instanceof Sequence ? (Sequence) this : null;
	}

	static class Equal extends CodepointMatch {
		final Codepoint point;

		Equal(Codepoint point) {
			this.point = point;
		}

		@Override
		boolean firstMatches(Codepoint point) {
			return this.point.equals(point);
		}

		@Override
		When whenSimple() {
			return SIMPLE_RANGE.contains(point)
					? When.ALWAYS
					: When.NEVER;
		}

		@Override
		public String toString() {
			return "'" + point + "'";
		}
	}

	static class NotEqual extends CodepointMatch {
		final Codepoint point;

		NotEqual(Codepoint point) {
			this.point = point;
		}

		@Override
		boolean firstMatches(Codepoint point) {
			return !this.point.equals(point);
		}

		@Override
		When whenSimple() {
			return When.MAYBE;
		}

		@Override
		public String toString() {
			return "[^" + point + "]";
		}
	}

	static class InRange extends CodepointMatch {
		final Range<Codepoint> range;
		final Codepoint lower;
		final Codepoint upper;

		InRange(Range<Codepoint> range) {
			assert range.hasLowerBound() && range.lowerBoundType() == BoundType.CLOSED;
			assert range.hasUpperBound() && range.upperBoundType() == BoundType.CLOSED;
			this.range = range;
			this.lower = range.lowerEndpoint();
			this.upper = range.upperEndpoint();
		}

		@Override
		boolean firstMatches(Codepoint point) {
			return range.contains(point);
		}

		@Override
		When whenSimple() {
			if (SIMPLE_RANGE.isConnected(range)) {
				return SIMPLE_RANGE.encloses(range)
						? When.ALWAYS
						: When.MAYBE;
			}
			return When.NEVER;
		}

		@Override
		public String toString() {
			return "[" + lower.toStringInRange() + "-" + upper.toStringInRange() + "]";
		}
	}

	static class NotInRange extends CodepointMatch {
		final Range<Codepoint> range;
		final Codepoint lower;
		final Codepoint upper;

		NotInRange(Range<Codepoint> range) {
			assert range.hasLowerBound() && range.lowerBoundType() == BoundType.CLOSED;
			assert range.hasUpperBound() && range.upperBoundType() == BoundType.CLOSED;
			this.range = range;
			this.lower = range.lowerEndpoint();
			this.upper = range.upperEndpoint();

		}

		@Override
		boolean firstMatches(Codepoint point) {
			return !range.contains(point);
		}

		@Override
		When whenSimple() {
			return When.MAYBE; // there may be more precise way to identify this
		}

		@Override
		public String toString() {
			return "[^" + lower.toStringInRange() + "-" + upper.toStringInRange() + "]";
		}
	}

	static class SmallTable extends CodepointMatch {
		final ImmutableSet<Codepoint> points;

		SmallTable(ImmutableSet<Codepoint> points) {
			this.points = points;
		}

		Vect<Boolean> table() {
			return Vect.from(Codepoint.SIMPLE_SET)
					.map(points::contains);
		}

		@Override
		boolean firstMatches(Codepoint point) {
			return points.contains(point);
		}

		@Override
		When whenSimple() {
			return When.ALWAYS;
		}

		@Override
		public String toString() {
			return "{" + Joiner.on("").join(points) + "}";
		}
	}

	static class CharMatches extends CodepointMatch {
		final ImmutableRangeSet<Codepoint> defined;
		final boolean not;

		CharMatches(ImmutableRangeSet<Codepoint> defined, boolean not) {
			this.defined = defined;
			this.not = not;
		}

		@Override
		When whenSimple() {
			return When.MAYBE;
		}

		@Override
		boolean firstMatches(Codepoint point) {
			return defined.contains(point) ^ not;
		}

		@Override
		public String toString() {
			return (not ? "[^" : "[")
					+ Joiner.on("").join(Vect.from(defined.asRanges()).map(this::rangeToString)) + "]";
		}

		private String rangeToString(Range<Codepoint> range) {
			Codepoint l = range.lowerEndpoint();
			Codepoint u = range.upperEndpoint();
			return l.equals(u)
					? l.toStringInRange()
					: (l.toStringInRange() + "-" + u.toStringInRange());
		}
	}

	static class Sequence extends CodepointMatch {
		final Vect<Codepoint> points;

		Sequence(Vect<Codepoint> points) {
			assert !points.isEmpty();
			this.points = points;
		}

		Sequence(String sequence) {
			this(sequence.codePoints()
					.mapToObj(Codepoint::of)
					.collect(Vect.to()));
		}

		@Override
		When whenSimple() {
			return SIMPLE_RANGE.contains(points.get(0))
					? When.ALWAYS
					: When.NEVER;
		}

		public Equal asFirst() {
			return new Equal(points.get(0));
		}

		@Override
		boolean firstMatches(Codepoint point) {
			return points.get(0).equals(point);
		}

		@Override
		public String toString() {
			return "'" + Joiner.on("").join(points) + "'";
		}
	}

	static Builder builder() {
		return new Builder();
	}

	static class Builder {
		private Builder() {}

		private Formedness form = Formedness.NONE;
		private final TreeRangeSet<Codepoint> ranges = TreeRangeSet.create();

		private enum Formedness {
			NONE,
			INCLUDE,
			INCLUDE_WITH_EXCLUDE,
			EXCLUDE;
		}

		Builder exclude(Range<Codepoint> range) {
			switch (form) {
			case NONE:
			case EXCLUDE:
				ranges.add(range);
				form = Formedness.EXCLUDE;
				return this;
			case INCLUDE:
			case INCLUDE_WITH_EXCLUDE:
				ranges.remove(range);
				form = Formedness.INCLUDE_WITH_EXCLUDE;
				return this;
			}
			throw Unreachable.exhaustive();
		}

		Builder include(Range<Codepoint> range) {
			switch (form) {
			case NONE:
			case INCLUDE:
				ranges.add(range);
				form = Formedness.INCLUDE;
				return this;
			case INCLUDE_WITH_EXCLUDE:
			case EXCLUDE:
				return illegal("Cannot add include range after exclude (^) range");
			}
			throw Unreachable.exhaustive();
		}

		Builder add(boolean negated, Codepoint point) {
			return negated
					? exclude(Range.singleton(point))
					: include(Range.singleton(point));
		}

		Builder add(boolean negated, Codepoint start, Codepoint end) {
			return negated
					? exclude(Range.closed(start, end))
					: include(Range.closed(start, end));
		}

		private Builder illegal(String string) {
			throw new IllegalArgumentException(string);
		}

		CodepointMatch build() {
			checkState(form != Formedness.NONE);

			class Factory {
				boolean exclusion = form == Formedness.EXCLUDE;
				ImmutableRangeSet<Codepoint> defined = ImmutableRangeSet.copyOf(ranges);

				CodepointMatch create() {
					ImmutableSet<Range<Codepoint>> definedRanges = defined.asRanges();

					Optional<Codepoint> singleCodepoint = singleCodepointFrom(definedRanges);
					if (singleCodepoint.isPresent()) {
						return exclusion
								? new CodepointMatch.NotEqual(singleCodepoint.get())
								: new CodepointMatch.Equal(singleCodepoint.get());
					}

					Optional<Range<Codepoint>> singleRange = singleRangeFrom(definedRanges);
					if (singleRange.isPresent()) {
						return exclusion
								? new CodepointMatch.NotInRange(singleRange.get())
								: new CodepointMatch.InRange(singleRange.get());
					}

					ImmutableRangeSet<Codepoint> actual = exclusion ? defined.complement() : defined;

					if (SIMPLE_RANGE.encloses(actual.span())) {
						return new CodepointMatch.SmallTable(actual.asSet(Codepoint.domain()));
					}

					return new CodepointMatch.CharMatches(defined, exclusion);
				}

				Optional<Range<Codepoint>> singleRangeFrom(ImmutableSet<Range<Codepoint>> definedRanges) {
					if (definedRanges.size() == 1) {
						Range<Codepoint> range = Iterables.getOnlyElement(definedRanges);
						return Optional.of(range);
					}
					return Optional.empty();
				}

				Optional<Codepoint> singleCodepointFrom(ImmutableSet<Range<Codepoint>> definedRanges) {
					if (definedRanges.size() == 1) {
						Range<Codepoint> range = Iterables.getOnlyElement(definedRanges);
						long distance = Codepoint.domain().distance(range.lowerEndpoint(), range.upperEndpoint());
						if (distance <= 1) {
							ContiguousSet<Codepoint> set = ContiguousSet.create(range, Codepoint.domain());
							if (set.size() == 1) {
								return Optional.of(Iterables.getOnlyElement(set));
							}
						}
					}
					return Optional.empty();
				}
			}

			return new Factory().create();
		}
	}

	static final Range<Codepoint> SIMPLE_RANGE = Codepoint.SIMPLE_RANGE;
}
