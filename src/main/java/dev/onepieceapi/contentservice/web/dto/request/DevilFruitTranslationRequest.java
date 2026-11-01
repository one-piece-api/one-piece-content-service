package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import jakarta.validation.constraints.Size;

/** The localized fields of a Devil Fruit in one language, as a draft is saved. */
public record DevilFruitTranslationRequest(@Size(max = DevilFruitTranslation.NAME_MAX_LENGTH) String name) {

}
