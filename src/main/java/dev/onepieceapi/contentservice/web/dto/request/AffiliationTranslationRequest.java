package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import jakarta.validation.constraints.Size;

/** The localized fields of an Affiliation in one language, as a draft is saved. */
public record AffiliationTranslationRequest(@Size(max = AffiliationTranslation.NAME_MAX_LENGTH) String name,
		@Size(max = AffiliationTranslation.DESCRIPTION_MAX_LENGTH) String description) {

}
