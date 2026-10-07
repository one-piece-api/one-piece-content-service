package dev.onepieceapi.contentservice.domain.workflow;

import java.util.List;
import java.util.Set;

/**
 * A version as one caller meets it: the version, the same for everyone who sees it, and
 * what that caller may do with it.
 *
 * @param <T> what a version of this kind of content says, e.g. a {@code DevilFruitType}
 * @param allowedActions decided by {@link TransitionPolicy}
 * @param overrideActions those of the allowed actions the caller may perform only through
 * {@code content:admin}
 * @param blockedActions those of the allowed actions the entity's own rules refuse, each
 * with why
 */
public record VersionAccess<T>(Version<T> version, Set<VersionAction> allowedActions,
		Set<VersionAction> overrideActions, List<BlockedAction> blockedActions) {

	/** Nothing blocked by the entity. */
	public VersionAccess(Version<T> version, Set<VersionAction> allowedActions, Set<VersionAction> overrideActions) {
		this(version, allowedActions, overrideActions, List.of());
	}

	/** A caller allowed nothing through an override. */
	public VersionAccess(Version<T> version, Set<VersionAction> allowedActions) {
		this(version, allowedActions, Set.of());
	}

}
