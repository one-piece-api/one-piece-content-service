package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The filters of the list of affiliations, as query parameters: those of every entity
 * list - see {@link ContentListRequest} - and the one of its own (implementation plan of
 * the Character, D16). All optional and combinable.
 *
 * @param kind only the affiliations whose version shown is of this kind
 */
public record AffiliationListRequest(VersionStatus status, String q, String author,
		@PositiveOrZero Integer updatedWithinDays, AffiliationKind kind) {

}
