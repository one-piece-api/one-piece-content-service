package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Set;
import java.util.UUID;

/**
 * One row of an entity section (UF-CNT-18): a content represented by a single version -
 * its most recent one visible to the caller, or the one in the status being filtered on.
 *
 * @param <T> what a version of this kind of content says, e.g. a {@code DevilFruitType}
 * @param onlineVersionNumber the version currently online, which may be a different one;
 * null when nothing is online
 * @param allowedActions what the caller may do with the version shown, decided by
 * {@link TransitionPolicy}
 * @param overrideActions those of the allowed actions the caller may perform only through
 * {@code content:admin}
 */
public record ContentSummary<T>(UUID contentId, Version<T> version, Integer onlineVersionNumber,
		Set<VersionAction> allowedActions, Set<VersionAction> overrideActions) {

	/** A caller allowed nothing through an override. */
	public ContentSummary(UUID contentId, Version<T> version, Integer onlineVersionNumber,
			Set<VersionAction> allowedActions) {
		this(contentId, version, onlineVersionNumber, allowedActions, Set.of());
	}

}
