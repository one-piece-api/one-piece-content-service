package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ErrorCode;

/**
 * This service's own error codes - see {@code one-piece-exception}'s {@code ErrorCode}
 * for why each service defines its own rather than sharing one closed registry. Prefixed
 * with {@code CONTENT_} so a client talking to multiple services can tell which one an
 * error code came from - same convention as {@code one-piece-user-service}'s
 * {@code UserErrorCode}.
 */
public enum ContentErrorCode implements ErrorCode {

	INVALID_LANGUAGE_CODE, INVALID_LANGUAGE_NAME, LANGUAGE_ALREADY_EXISTS, LANGUAGE_NOT_FOUND, LANGUAGE_IN_USE,
	DEVIL_FRUIT_TYPE_NOT_FOUND, VERSION_NOT_FOUND, VERSION_ACTION_FORBIDDEN, VERSION_ACTION_CONFLICT,
	VALUE_ALREADY_USED, TRANSLATION_LANGUAGE_UNKNOWN;

	@Override
	public String code() {
		return "CONTENT_" + name();
	}

}
