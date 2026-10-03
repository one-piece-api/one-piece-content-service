package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.UUID;

/**
 * The workflow side of one numbered revision of a content, of whatever entity type: born
 * with its draft and carrying its own status
 * (docs/user-flows/content-editorial-workflow.md 4.1). What the version says is in its
 * entity's own table, e.g. {@link DevilFruitTypeVersionEntity}. See
 * {@code db/migration/V2__content_versions.sql} for the invariants the database enforces
 * and {@code docs/adr/0002-version-table-with-status.md} for the split.
 */
@Entity
@Table(name = "content_version")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@FieldNameConstants
public class ContentVersionEntity {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	private UUID contentId;

	private int versionNumber;

	/** The version this one was opened from; null for the first one. */
	private Integer basedOnNumber;

	@Embedded
	@AttributeOverride(name = "userId", column = @Column(name = "author_user_id"))
	@AttributeOverride(name = "username", column = @Column(name = "author_username"))
	@AttributeOverride(name = "email", column = @Column(name = "author_email"))
	private UserEmbeddable author;

	@Setter
	@Enumerated(EnumType.STRING)
	private VersionStatus status;

	/**
	 * The reviewer holding the version while {@code IN_REVIEW}; null when nobody does.
	 */
	@Setter
	@Embedded
	@AttributeOverride(name = "userId", column = @Column(name = "claimant_user_id"))
	@AttributeOverride(name = "username", column = @Column(name = "claimant_username"))
	@AttributeOverride(name = "email", column = @Column(name = "claimant_email"))
	private UserEmbeddable claimant;

	@Setter
	private String rejectionReason;

	private Instant createdAt;

	@Setter
	private Instant updatedAt;

	/**
	 * Optimistic lock: a change written from an older read of this row fails instead of
	 * silently overwriting the change made in between (see
	 * {@code db/migration/V4__content_version_lock.sql}).
	 */
	@Version
	private long lockVersion;

	/** Takes the version to another status, and notes when. */
	public void moveTo(VersionStatus newStatus, Instant now) {
		this.status = newStatus;
		this.updatedAt = now;
	}

	/**
	 * Sends the version to review, unclaimed. The reason of an earlier rejection was
	 * about the round that failed, not this one: it goes.
	 */
	public void submit(Instant now) {
		moveTo(VersionStatus.IN_REVIEW, now);
		this.rejectionReason = null;
	}

	/** The claimant passes the review, which ends their claim. */
	public void approve(Instant now) {
		moveTo(VersionStatus.READY_TO_PUBLISH, now);
		release();
	}

	/**
	 * The claimant fails the review, which ends their claim. The reason stays until the
	 * version is submitted again.
	 */
	public void reject(String reason, Instant now) {
		moveTo(VersionStatus.REJECTED, now);
		this.rejectionReason = reason;
		release();
	}

	/**
	 * A reviewer takes the version. Not a change to what it says, so its update time
	 * stays.
	 */
	public void claimBy(UserEmbeddable reviewer) {
		this.claimant = reviewer;
	}

	/** Nobody holds the version any more. */
	public void release() {
		this.claimant = null;
	}

}
