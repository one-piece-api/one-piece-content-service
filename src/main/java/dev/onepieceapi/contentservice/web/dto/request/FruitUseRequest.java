package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A Devil Fruit of a Character, as a draft is saved (implementation plan of the
 * Character, D12; workflow 3.4.8): former once its window has an end.
 *
 * @param devilFruit the content id of the fruit
 * @param discovered as {@link WindowedRequest#discovered()}
 * @param ended where the reader learns the character lost its power; left out for none
 */
public record FruitUseRequest(@NotNull @Schema(description = "The content id of a Devil Fruit") UUID devilFruit,
		FirstAppearanceRequest discovered, FirstAppearanceRequest ended) implements WindowedRequest {

}
