package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * The filters of an entity list, as query parameters - the same for every kind of
 * content. All optional and combinable.
 *
 * @param status only contents having a visible version in this status, shown by it
 * @param q text contained in what the version says
 * @param author the id of the author of the version shown
 * @param updatedWithinDays updated today (0) or in the last N days
 */
public record ContentListRequest(VersionStatus status, String q, UUID author,
		@PositiveOrZero Integer updatedWithinDays) {

}
