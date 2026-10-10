package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import jakarta.validation.constraints.Size;

/** The localized fields of a subcategory in one language, as a draft is saved. */
public record DevilFruitTypeSubcategoryTranslationRequest(
		@Size(max = DevilFruitTypeSubcategoryTranslation.NAME_MAX_LENGTH) String name,
		@Size(max = DevilFruitTypeSubcategoryTranslation.DESCRIPTION_MAX_LENGTH) String description) {

}
