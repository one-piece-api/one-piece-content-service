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

	@Setter
	private String romaji;

	@Setter
	@Enumerated(EnumType.STRING)
	private WorkingRevisionStatus status;

	private Instant createdAt;

	@Setter
	private Instant updatedAt;

	public WorkingRevisionEntity(UUID id, UUID itemId, UUID authorId, WorkingRevisionStatus status, Instant createdAt) {
		this.id = id;
		this.itemId = itemId;
		this.authorId = authorId;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

}
