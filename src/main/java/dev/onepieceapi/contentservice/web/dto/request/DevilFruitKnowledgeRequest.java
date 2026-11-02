package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * What the reader believes of a Devil Fruit at its first appearance (workflow 3.7.1), for
 * the fields some reveal names; a value left out is not part of it.
 *
 * @param type the id of the content of a Devil Fruit Type, as
 * {@link DevilFruitRequest#type()}
 * @param subcategory one of the subcategories of that type
 * @param translations the name per language code
 */
public record DevilFruitKnowledgeRequest(@Size(max = DevilFruit.ROMAJI_MAX_LENGTH) String romaji,
		@Schema(description = "The content id of a Devil Fruit Type") UUID type,
		@Schema(description = "The id of a subcategory of that type") UUID subcategory,
		Map<String, @Valid DevilFruitTranslationRequest> translations) {

}
