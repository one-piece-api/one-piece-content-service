package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.validation.ContentValidator;
import dev.onepieceapi.exception.NotFoundException;

import java.time.Instant;
import java.util.UUID;

/**
 * What the generic {@link ContentService} needs to know of one entity to run its whole
 * workflow (implementation plan of the Devil Fruit, D8): where its versions are stored,
 * how they read and are written, how they are validated, and how a content of it that
 * cannot be found is reported. One per entity, e.g. {@link DevilFruitTypeDefinition} -
 * the descriptor the service is built with (Strategy pattern).
 *
 * @param <T> what a version of the entity says
 * @param <E> the entity's version table
 */
public interface ContentDefinition<T extends ContentBody<T>, E extends VersionBodyEntity> {

	EntityType entityType();

	VersionBodyRepository<E> repository();

	ContentValidator<T, E> validator();

	/** A stored version, workflow and content together. */
	Version<T> toDomain(E entity);

	/** A new version of the entity around its workflow, saying nothing yet. */
	E newVersion(ContentVersionEntity workflow);

	/** Makes the version say what was given, in place of what it said. */
	void rewrite(E entity, T body, Instant now);

	/** The entity's rules about its data; by default, none. */
	default ContentRules<T> rules() {
		return ContentRules.none();
	}

	/** A content of this entity that does not exist for the caller. */
	NotFoundException notFound(UUID contentId);

}
