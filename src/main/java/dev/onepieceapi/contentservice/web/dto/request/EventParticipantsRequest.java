package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * What takes part in an event (implementation plan of the timeline, T5), typed, no role:
 * only Devil Fruits today, characters to come beside them.
 *
 * @param devilFruits the ids of the contents of the fruits, each once; whether one can be
 * chosen is answered by the list of Devil Fruits
 */
public record EventParticipantsRequest(
		@Schema(description = "The content ids of the Devil Fruits") List<@NotNull UUID> devilFruits) {

}
