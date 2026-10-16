package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * One reveal of a Devil Fruit (implementation plan of the timeline, T9; ADR-0010): the
 * chapter it happens in and only the fields revealed there, each with the value believed
 * before. A field left out is not part of it.
 *
 * @param revealedAt where the reader learns better
 * @param type the content id of the Devil Fruit Type believed; given, it is believed with
 * the subcategory, none when that is left out
 * @param subcategory a subcategory of the type in force at the reveal
 * @param translations what was believed per language code
 * @param titles the short title of the reveal per language code
 */
public record DevilFruitRevealRequest(@Valid RevealedAtRequest revealedAt,
		@Size(max = DevilFruit.ROMAJI_MAX_LENGTH) String romaji,
		@Schema(description = "The content id of the Devil Fruit Type believed; left out when not part of it") UUID type,
		@Schema(description = "The id of a subcategory of the type in force at the reveal") UUID subcategory,
		Map<String, @Valid DevilFruitTranslationRequest> translations,
		@Schema(description = "Short title of the reveal per language code; required at submission") Map<String, @Size(
				max = DevilFruitReveal.TITLE_MAX_LENGTH) String> titles) {

}
