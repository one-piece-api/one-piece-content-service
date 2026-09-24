package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The stable identity a content item keeps across every working revision, and every
 * published version (Step 5+). {@code liveVersionId} (nullable) is the "live pointer"
 * (4.1): null until a first Publish (UF-CNT-07) sets it; Rollback (Step 7) repoints it to
 * an older snapshot; Retire (Step 8) clears it. The item's own PUBLISHED/RETIRED display
 * state is always derived from this field plus whether any {@code ContentVersionEntity}
 * exists for it, never stored directly.
 */
@Entity
@Table(name = "devil_fruit_type_item")
@Getter
@NoArgsConstructor
public class DevilFruitTypeItemEntity {

	@Id
	private UUID id;

	@Setter
	@Column(name = "live_version_id")
	private UUID liveVersionId;

	private Instant createdAt;

	public DevilFruitTypeItemEntity(UUID id, Instant createdAt) {
		this.id = id;
		this.createdAt = createdAt;
	}

}
