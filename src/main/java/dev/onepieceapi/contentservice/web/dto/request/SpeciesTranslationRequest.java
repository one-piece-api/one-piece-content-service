package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.species.SpeciesTranslation;
import jakarta.validation.constraints.Size;

/** The localized fields of a Species in one language, as a draft is saved. */
public record SpeciesTranslationRequest(@Size(max = SpeciesTranslation.NAME_MAX_LENGTH) String name,
		@Size(max = SpeciesTranslation.DESCRIPTION_MAX_LENGTH) String description) {

}
