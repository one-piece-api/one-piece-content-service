package dev.onepieceapi.contentservice.domain.event;

/**
 * A year of the One Piece calendar, or a span of years, before the story starts
 * (docs/user-flows/content-editorial-workflow.md 3.1.8; implementation plan of the
 * timeline, T4): "1498", "c. 1500", "c. 700-800".
 *
 * @param yearFrom the first year, required
 * @param yearTo the last year, for a span; null for one year
 * @param approximate whether the years are not known for sure
 */
public record WorldDate(Integer yearFrom, Integer yearTo, boolean approximate) {

	/** The last year it may be: its last, or its only one. */
	public Integer latestYear() {
		return this.yearTo == null ? this.yearFrom : this.yearTo;
	}

	/**
	 * Whether it certainly ends before the other one starts: its last year comes before
	 * the other's first (T4, 4a) - two dates in the same year are never in the wrong
	 * order.
	 */
	public boolean certainlyBefore(WorldDate other) {
		return latestYear() < other.yearFrom();
	}

}
