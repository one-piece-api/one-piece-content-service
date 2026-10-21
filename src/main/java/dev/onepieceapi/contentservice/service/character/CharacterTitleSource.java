package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.CharacterVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.service.content.VersionBodyTitleSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A Character is called by its name in each language, or else by its romaji. */
@Component
class CharacterTitleSource extends VersionBodyTitleSource<Character, CharacterVersionEntity> {

	@Autowired
	CharacterTitleSource(CharacterVersionRepository repository) {
		super(EntityType.CHARACTER, repository, CharacterVersionMapper::toDomain);
	}

}
