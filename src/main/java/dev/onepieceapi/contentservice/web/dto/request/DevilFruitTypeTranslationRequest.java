package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import jakarta.validation.constraints.Size;

/** The localized fields of a Devil Fruit Type in one language, as a draft is saved. */
public record DevilFruitTypeTranslationRequest(@Size(max = DevilFruitTypeTranslation.NAME_MAX_LENGTH) String name,
		@Size(max = DevilFruitTypeTranslation.DESCRIPTION_MAX_LENGTH) String description,
		@Size(max = DevilFruitTypeTranslation.ADVANTAGES_MAX_LENGTH) String advantages,
		@Size(max = DevilFruitTypeTranslation.DISADVANTAGES_MAX_LENGTH) String disadvantages) {

}
