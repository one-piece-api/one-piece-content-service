package dev.onepieceapi.contentservice.domain.security;

import java.util.UUID;

/**
 * A user as this service knows one: what their token carried when they acted. Used for
 * the caller of a request, for the author and the claimant of a version and for the actor
 * of an audit record - there is no user directory here to resolve anything more. Neither
 * the username nor the e-mail of an account can change, so a copy never goes stale.
 *
 * @param username what the user is shown as
 */
public record User(UUID id, String username, String email) {

}
