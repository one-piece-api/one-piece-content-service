package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One encyclopedia entry, of one entity type
 * (docs/user-flows/content-editorial-workflow.md 4.1). It holds no content: everything
 * lives on its versions, see {@link ContentVersionEntity}.
 */
@Entity
@Table(name = "content_item")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContentItemEntity {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	private EntityType entityType;

	private Instant createdAt;

}
