package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.ErrorCode;

/**
 * This service's own error codes - see {@code one-piece-exception}'s {@code ErrorCode}
 * for why each service defines its own rather than sharing one closed registry. Prefixed
 * with {@code CONTENT_} so a client talking to multiple services can tell which one an
 * error code came from - same convention as {@code one-piece-user-service}'s
 * {@code UserErrorCode}.
 */
public enum ContentErrorCode implements ErrorCode {

	WORKING_REVISION_NOT_FOUND, UNKNOWN_LANGUAGE;

	@Override
	public String code() {
		return "CONTENT_" + name();
	}

}
