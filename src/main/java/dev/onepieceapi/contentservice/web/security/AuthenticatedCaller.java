package dev.onepieceapi.contentservice.web.security;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;

import java.util.Set;

/**
 * A validated request's identity, resolved once past the raw {@code Jwt} - just what this
 * service ever needs of it: who is calling (ownership checks, audit actor) and which of
 * this service's permissions they hold (what they may see and do, beyond the per-endpoint
 * gate of {@code SecuredEndpoint}). No status or roles here: this service never resolves
 * or checks any of those.
 */
public record AuthenticatedCaller(User user, Set<Permission> permissions) {

}
