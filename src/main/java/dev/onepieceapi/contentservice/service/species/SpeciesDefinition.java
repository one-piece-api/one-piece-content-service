package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.SpeciesVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.SpeciesNotFoundException;
import dev.onepieceapi.contentservice.service.validation.SpeciesValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** The species, as the generic workflow sees it. */
@Component
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public class SpeciesDefinition implements ContentDefinition<Species, SpeciesVersionEntity> {

	private final SpeciesVersionRepository repository;

	private final SpeciesValidator validator;

	private final SpeciesRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.SPECIES;
	}

	@Override
	public Version<Species> toDomain(SpeciesVersionEntity entity) {
		return SpeciesVersionMapper.toDomain(entity);
	}

	@Override
	public SpeciesVersionEntity newVersion(ContentVersionEntity workflow) {
		return new SpeciesVersionEntity(workflow);
	}

	@Override
	public void rewrite(SpeciesVersionEntity entity, Species body, Instant now) {
		SpeciesVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new SpeciesNotFoundException(contentId);
	}

}
