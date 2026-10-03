package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.workflow.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Why a review failed (UF-CNT-06): what the author must fix. Read without the space
 * around it, so that the length is checked on what is actually stored.
 */
public record RejectVersionRequest(@NotNull @Size(min = Version.REJECTION_REASON_MIN_LENGTH,
		max = Version.REJECTION_REASON_MAX_LENGTH) String reason) {

	public RejectVersionRequest {
		reason = reason == null ? null : reason.strip();
	}

}
