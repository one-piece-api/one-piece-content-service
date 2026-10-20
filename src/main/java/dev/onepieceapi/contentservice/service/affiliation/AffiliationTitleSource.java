package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.service.content.VersionBodyTitleSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** An Affiliation is called by its name in each language, or else by its romaji. */
@Component
class AffiliationTitleSource extends VersionBodyTitleSource<Affiliation, AffiliationVersionEntity> {

	@Autowired
	AffiliationTitleSource(AffiliationVersionRepository repository) {
		super(EntityType.AFFILIATION, repository, AffiliationVersionMapper::toDomain);
	}

}
