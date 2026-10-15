package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * One knowledge phase of a Devil Fruit (implementation plan of the timeline, T9;
 * ADR-0010): the chapter that supersedes it and only the fields revealed there, each with
 * the value believed before. A field left out is not part of it.
 *
 * @param supersededAt where the reader learns better
 * @param type the content id of the Devil Fruit Type believed; given, it is believed with
 * the subcategory, none when that is left out
 * @param subcategory a subcategory of the type in force at the phase
 * @param translations what was believed per language code
 */
public record DevilFruitPhaseRequest(@Valid SupersededAtRequest supersededAt,
		@Size(max = DevilFruit.ROMAJI_MAX_LENGTH) String romaji,
		@Schema(description = "The content id of the Devil Fruit Type believed; left out when not part of it") UUID type,
		@Schema(description = "The id of a subcategory of the type in force at the phase") UUID subcategory,
		Map<String, @Valid DevilFruitTranslationRequest> translations) {

}
