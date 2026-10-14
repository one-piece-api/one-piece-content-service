package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * The filters of the list of chapters, as query parameters: those of every entity list -
 * see {@link ContentListRequest} - and those of its own. All optional and combinable.
 *
 * @param arc only the chapters whose version shown points to this arc (its content id)
 * @param volume only the chapters whose version shown points to this volume (its content
 * id)
 */
public record ChapterListRequest(VersionStatus status, String q, String author,
		@PositiveOrZero Integer updatedWithinDays, @Schema(description = "The content id of an arc") UUID arc,
		@Schema(description = "The content id of a volume") UUID volume) {

}
