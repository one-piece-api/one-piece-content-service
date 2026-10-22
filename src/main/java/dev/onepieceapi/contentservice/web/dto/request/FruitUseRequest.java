package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A Devil Fruit of a Character, as a draft is saved (implementation plan of the
 * Character, D12).
 *
 * @param devilFruit the content id of the fruit
 * @param former true once the character no longer has its power; left out is false
 */
public record FruitUseRequest(@NotNull @Schema(description = "The content id of a Devil Fruit") UUID devilFruit,
		Boolean former) {

	/** Whether the character lost the power; not saying so is not. */
	public boolean isFormer() {
		return Boolean.TRUE.equals(this.former);
	}

}
