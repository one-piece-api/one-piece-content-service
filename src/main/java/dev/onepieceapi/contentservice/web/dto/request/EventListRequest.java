package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * The filters of the list of events, as query parameters: those of every entity list -
 * see {@link ContentListRequest} - and those of its own. All optional and combinable.
 *
 * @param devilFruit only the events whose version shown has this Devil Fruit taking part
 * (its content id): the events of a fruit
 */
public record EventListRequest(VersionStatus status, String q, String author, @PositiveOrZero Integer updatedWithinDays,
		@Schema(description = "The content id of a Devil Fruit taking part") UUID devilFruit) {

}
