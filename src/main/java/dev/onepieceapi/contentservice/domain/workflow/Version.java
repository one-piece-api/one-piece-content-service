package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.User;
import lombok.Builder;

import java.time.Instant;
import java.util.Objects;

/**
 * One numbered revision of a {@link Content}
 * (docs/user-flows/content-editorial-workflow.md 4.1): who wrote it and where it stands -
 * the same for every kind of content - and what it says, which is not.
 *
 * @param <T> what a version of this kind of content says, e.g. a {@code DevilFruitType}
 * @param basedOn the version it was opened from; null for the first one
 * @param claimant the reviewer holding it while {@code IN_REVIEW}, if any
 * @param rejectionReason why the last review failed: kept from the rejection until the
 * version is submitted again, so that its author still reads what to fix once it is back
 * in draft
 * @param body what this version says
 */
@Builder
public record Version<T>(int number, Integer basedOn, VersionStatus status, User author, User claimant,
		String rejectionReason, T body, Instant createdAt, Instant updatedAt) {

	/** A rejection reason says what to fix: at least a short sentence (UF-CNT-06). */
	public static final int REJECTION_REASON_MIN_LENGTH = 8;

	public static final int REJECTION_REASON_MAX_LENGTH = 2000;

	/** Whether this version has been online at some point. */
	public boolean everPublished() {
		return this.status.everPublished();
	}

	/**
	 * Whether this is the first version of its content. A first version still in draft is
	 * the only one its content has: a draft is always the latest version, and a later one
	 * can only be opened from a closed version.
	 */
	public boolean isFirst() {
		return this.number == 1;
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

	/** Whether this version says exactly that - every field, case included. */
	public boolean says(T otherBody) {
		return Objects.equals(this.body, otherBody);
	}

}
