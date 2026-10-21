package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.SpeciesVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.VersionBodyTitleSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A Species is called by its name in each language, or else by its romaji. */
@Component
class SpeciesTitleSource extends VersionBodyTitleSource<Species, SpeciesVersionEntity> {

	@Autowired
	SpeciesTitleSource(SpeciesVersionRepository repository) {
		super(EntityType.SPECIES, repository, SpeciesVersionMapper::toDomain);
	}

}
