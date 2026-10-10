package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

/**
 * A subcategory of a Devil Fruit Type, as a draft is saved.
 *
 * @param id the id of a subcategory the draft already has; left out for a new one, which
 * the server gives its id
 * @param translations name and description per language code
 */
public record DevilFruitTypeSubcategoryRequest(
		@Schema(description = "The id of a subcategory the draft already has; left out for a new one") UUID id,
		Map<String, @Valid DevilFruitTypeSubcategoryTranslationRequest> translations) {

}
