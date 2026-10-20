package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.AffiliationNotFoundException;
import dev.onepieceapi.contentservice.service.validation.AffiliationValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** The affiliation, as the generic workflow sees it. */
@Component
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public class AffiliationDefinition implements ContentDefinition<Affiliation, AffiliationVersionEntity> {

	private final AffiliationVersionRepository repository;

	private final AffiliationValidator validator;

	private final AffiliationRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.AFFILIATION;
	}

	@Override
	public Version<Affiliation> toDomain(AffiliationVersionEntity entity) {
		return AffiliationVersionMapper.toDomain(entity);
	}

	@Override
	public AffiliationVersionEntity newVersion(ContentVersionEntity workflow) {
		return new AffiliationVersionEntity(workflow);
	}

	@Override
	public void rewrite(AffiliationVersionEntity entity, Affiliation body, Instant now) {
		AffiliationVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new AffiliationNotFoundException(contentId);
	}

}
