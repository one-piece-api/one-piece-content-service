package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * The filters of the list of Devil Fruits, as query parameters: those of every entity
 * list - see {@link ContentListRequest} - and the one of its own. All optional and
 * combinable.
 *
 * @param type only the fruits whose version shown points to this Devil Fruit Type (its
 * content id)
 */
public record DevilFruitListRequest(VersionStatus status, String q, String author,
		@PositiveOrZero Integer updatedWithinDays,
		@Schema(description = "The content id of a Devil Fruit Type") UUID type) {

}
