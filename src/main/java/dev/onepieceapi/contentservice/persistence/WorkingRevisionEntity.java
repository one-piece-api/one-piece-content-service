package dev.onepieceapi.contentservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A mutable draft of one {@link DevilFruitTypeItemEntity}, owned by exactly one author.
 * An item may have several of these at once, one per author
 * (docs/user-flows/authentication-and-user-management.md 4.1/7.5) - there is deliberately
 * no uniqueness constraint on {@code itemId} alone.
 */
@Entity
@Table(name = "devil_fruit_type_working_revision")
@Getter
@NoArgsConstructor
public class WorkingRevisionEntity {

	@Id
	private UUID id;

	@Column(name = "item_id", nullable = false)
	private UUID itemId;

	@Column(name = "author_id", nullable = false)
	private UUID authorId;

	/**
	 * Denormalized from the author's own JWT at creation time - content-service has no
	 * user directory to resolve a bare id against, and the review queue (Step 3) must
	 * show every author's identity to every REVIEWER, not just the caller's own. Nullable
	 * because rows created before this column existed have none.
	 */
	@Column(name = "author_email")
	private String authorEmail;

	@Setter
	private String romaji;

	@Setter
	@Enumerated(EnumType.STRING)
	private WorkingRevisionStatus status;

	/**
	 * The REVIEWER currently claiming it, while {@code IN_REVIEW} - see
	 * {@link #claimedByEmail}.
	 */
	@Setter
	@Column(name = "claimed_by")
	private UUID claimedBy;

	/**
	 * Same denormalization rationale as {@link #authorEmail}, for whoever holds
	 * {@link #claimedBy}.
	 */
	@Setter
	@Column(name = "claimed_by_email")
	private String claimedByEmail;

	/**
	 * The most recent rejection's reason, visible to the author until overwritten or
	 * resolved.
	 */
	@Setter
	@Column(name = "rejection_reason")
	private String rejectionReason;

	private Instant createdAt;

	@Setter
	private Instant updatedAt;

	public WorkingRevisionEntity(UUID id, UUID itemId, UUID authorId, String authorEmail, WorkingRevisionStatus status,
			Instant createdAt) {
		this.id = id;
		this.itemId = itemId;
		this.authorId = authorId;
		this.authorEmail = authorEmail;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

}
