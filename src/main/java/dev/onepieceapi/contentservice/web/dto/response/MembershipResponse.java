package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;

/**
 * A membership of a Character: current while one of its periods has no end, derived.
 *
 * @param affiliation the affiliation as of today, with its kind
 * @param periods every stay in it, in order
 */
public record MembershipResponse(AffiliationReferenceResponse affiliation, List<MembershipPeriodResponse> periods) {

}
