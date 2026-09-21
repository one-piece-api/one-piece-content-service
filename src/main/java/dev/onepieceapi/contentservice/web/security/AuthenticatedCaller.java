package dev.onepieceapi.contentservice.web.security;

import java.util.UUID;

/**
 * A validated request's identity, resolved once past the raw {@code Jwt} - just the two
 * claims this service ever needs (ownership checks, audit actor). Deliberately not a full
 * {@code User} domain object like {@code one-piece-user-service}'s: no status, username
 * or roles here, since this service never resolves or checks any of those - only
 * permissions (see {@link Permission}) and, for the caller's own working revisions, this
 * id/email.
 */
public record AuthenticatedCaller(UUID id, String email) {

}
