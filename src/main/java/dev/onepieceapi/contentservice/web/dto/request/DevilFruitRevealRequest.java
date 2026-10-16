package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One reveal of a Devil Fruit (implementation plan of the timeline, T13; ADR-0010): the
 * chapter it happens in, the fields the reader learns there and, for those not yet the
 * truth, the value learned. The last reveal of a field gives no value: it reveals the
 * truth.
 *
 * @param revealedAt where the reader learns
 * @param titles the short title of the reveal per language code
 * @param fields the fields learned there
 * @param type the content id of the Devil Fruit Type learned; given, it goes with the
 * subcategory, none when that is left out
 * @param subcategory a subcategory of that type
 * @param translations the translated values learned per language code
 */
public record DevilFruitRevealRequest(@Valid RevealedAtRequest revealedAt,
		@Schema(description = "Short title of the reveal per language code; required at submission") Map<String, @Size(
				max = DevilFruitReveal.TITLE_MAX_LENGTH) String> titles,
		@Schema(description = "The fields the reader learns at the reveal") Set<RevealedField> fields,
		@Size(max = DevilFruit.ROMAJI_MAX_LENGTH) String romaji,
		@Schema(description = "The content id of the Devil Fruit Type learned, when not yet the truth") UUID type,
		@Schema(description = "The id of a subcategory of that type") UUID subcategory,
		Map<String, @Valid DevilFruitTranslationRequest> translations) {

}
