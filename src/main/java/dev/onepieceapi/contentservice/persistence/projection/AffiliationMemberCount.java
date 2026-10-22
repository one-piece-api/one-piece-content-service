package dev.onepieceapi.contentservice.persistence.projection;

import java.util.UUID;

/** How many characters online belong to an affiliation. */
public record AffiliationMemberCount(UUID affiliationContentId, long count) {

}
