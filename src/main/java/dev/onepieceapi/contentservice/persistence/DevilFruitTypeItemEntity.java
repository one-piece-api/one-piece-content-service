package dev.onepieceapi.contentservice.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The stable identity a content item keeps across every working revision, and later every
 * published version (Step 5+). Minimal for now - {@code live_version_id} and similar
 * arrive with the migration that actually needs them, not speculatively here.
 */
@Entity
@Table(name = "devil_fruit_type_item")
@Getter
@NoArgsConstructor
public class DevilFruitTypeItemEntity {

	@Id
	private UUID id;

	private Instant createdAt;

	public DevilFruitTypeItemEntity(UUID id, Instant createdAt) {
		this.id = id;
		this.createdAt = createdAt;
	}

}
