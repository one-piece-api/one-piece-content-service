package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when an entity's own rule refuses an action the version is in a state for (e.g.
 * publishing a fruit whose type is not online), whoever asks - even
 * {@code content:admin}: it relaxes who may act, not the data invariants. Carries the
 * reason and its detail, as {@code blockedActions} does, and is told apart from
 * {@link VersionActionConflictException}, which is a version in the wrong state.
 */
public class VersionActionBlockedException extends ConflictException {

	public VersionActionBlockedException(VersionAction action, ActionBlock block) {
		super(ContentErrorCode.VERSION_ACTION_BLOCKED, "This version cannot " + action + ": " + block.reason());
		withDetail("reason", block.reason());
		withDetail("detail", block.detail());
	}

}
