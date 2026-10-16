package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * What the reader knows of a Devil Fruit at its first appearance, for the fields a reveal
 * changes later (implementation plan of the timeline, T13). A field left out is not part
 * of it - unknown, when a reveal names it.
 *
 * @param type the content id of a Devil Fruit Type; given, it goes with the subcategory,
 * none when that is left out
 * @param subcategory a subcategory of that type
 * @param translations the translated values per language code
 */
public record DevilFruitBeliefRequest(@Size(max = DevilFruit.ROMAJI_MAX_LENGTH) String romaji,
		@Schema(description = "The content id of a Devil Fruit Type") UUID type,
		@Schema(description = "The id of a subcategory of that type") UUID subcategory,
		Map<String, @Valid DevilFruitTranslationRequest> translations) {

}
