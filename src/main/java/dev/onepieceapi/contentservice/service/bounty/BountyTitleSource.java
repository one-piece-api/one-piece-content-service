package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.BountyVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.service.content.VersionBodyTitleSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A bounty has no name in any language: it is called by its key text. */
@Component
class BountyTitleSource extends VersionBodyTitleSource<Bounty, BountyVersionEntity> {

	@Autowired
	BountyTitleSource(BountyVersionRepository repository) {
		super(EntityType.BOUNTY, repository, BountyVersionMapper::toDomain);
	}

}
