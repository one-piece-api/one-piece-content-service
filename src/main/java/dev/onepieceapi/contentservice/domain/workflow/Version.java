package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.User;
import lombok.Builder;

import java.time.Instant;

/**
 * One numbered revision of a {@link Content}
 * (docs/user-flows/content-editorial-workflow.md 4.1): who wrote it and where it stands -
 * the same for every kind of content - and what it says, which is not.
 *
 * @param <T> what a version of this kind of content says, e.g. a {@code DevilFruitType}
 * @param basedOn the version it was opened from; null for the first one
 * @param claimant the reviewer holding it while {@code IN_REVIEW}, if any
 * @param rejectionReason why the last review failed, while {@code REJECTED}
 * @param body what this version says
 */
@Builder
public record Version<T>(int number, Integer basedOn, VersionStatus status, User author, User claimant,
		String rejectionReason, T body, Instant createdAt, Instant updatedAt) {

	/** Whether this version has been online at some point. */
	public boolean everPublished() {
		return this.status.everPublished();
	}

	/** Whether this version is the one currently online. */
	public boolean isOnline() {
		return this.status == VersionStatus.PUBLISHED;
	}

	public boolean isAuthoredBy(User user) {
		return this.author.isSameAs(user);
	}

	/** Whether a reviewer holds this version. */
	public boolean isClaimed() {
		return this.claimant != null;
	}

	public boolean isClaimedBy(User user) {
		return isClaimed() && this.claimant.isSameAs(user);
	}

}
