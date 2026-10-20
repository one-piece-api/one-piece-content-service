package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.BountyVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.BountyNotFoundException;
import dev.onepieceapi.contentservice.service.validation.BountyValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** The bounty, as the generic workflow sees it. */
@Component
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public class BountyDefinition implements ContentDefinition<Bounty, BountyVersionEntity> {

	private final BountyVersionRepository repository;

	private final BountyValidator validator;

	private final BountyRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.BOUNTY;
	}

	@Override
	public Version<Bounty> toDomain(BountyVersionEntity entity) {
		return BountyVersionMapper.toDomain(entity);
	}

	@Override
	public BountyVersionEntity newVersion(ContentVersionEntity workflow) {
		return new BountyVersionEntity(workflow);
	}

	@Override
	public void rewrite(BountyVersionEntity entity, Bounty body, Instant now) {
		BountyVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new BountyNotFoundException(contentId);
	}

}
