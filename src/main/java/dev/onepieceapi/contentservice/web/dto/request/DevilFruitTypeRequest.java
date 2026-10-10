package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * What a draft of a Devil Fruit Type is saved with - everything the version says, in
 * place of what it said. Any part may be missing: a draft is saved incomplete. Lengths
 * are the only limit checked here; whether a value is free to use is for the service to
 * say.
 *
 * @param translations name, description, advantages and disadvantages per language code;
 * a language left out, or with nothing written in it, has no translation
 * @param subcategories every subcategory, in the order they are shown; left out for none
 */
public record DevilFruitTypeRequest(@Size(max = DevilFruitType.ROMAJI_MAX_LENGTH) String romaji,
		Map<String, @Valid DevilFruitTypeTranslationRequest> translations,
		List<@Valid DevilFruitTypeSubcategoryRequest> subcategories) {

}
