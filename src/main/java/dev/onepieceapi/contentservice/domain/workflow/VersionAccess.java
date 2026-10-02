package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Set;

/**
 * A version as one caller meets it: the version, the same for everyone who sees it, and
 * what that caller may do with it.
 *
 * @param <T> what a version of this kind of content says, e.g. a {@code DevilFruitType}
 * @param allowedActions decided by {@link TransitionPolicy}
 */
public record VersionAccess<T>(Version<T> version, Set<VersionAction> allowedActions) {

}
