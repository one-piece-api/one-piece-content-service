package dev.onepieceapi.contentservice.domain.character;

import java.util.List;
import java.util.UUID;

/**
 * A character's place in an affiliation (implementation plan of the Character, D10;
 * workflow 3.4.7): the affiliation and every stay in it, its periods, in order. Current
 * while one of its periods is, former when all are: derived, never stored. Part of the
 * character's version, in the order it is shown. A draft may hold none; submitting
 * requires one.
 *
 * @param affiliationContentId the content id of the affiliation
 * @param periods in order; none is never null
 */
public record Membership(UUID affiliationContentId, List<MembershipPeriod> periods) {

	public Membership {
		periods = periods == null ? List.of() : List.copyOf(periods);
	}

	Membership normalized() {
		return new Membership(this.affiliationContentId,
				this.periods.stream().map(MembershipPeriod::normalized).toList());
	}

	/** Whether one of its periods holds a Marine rank. */
	public boolean hasRank() {
		return this.periods.stream().anyMatch(period -> period.marineRank() != null);
	}

}
