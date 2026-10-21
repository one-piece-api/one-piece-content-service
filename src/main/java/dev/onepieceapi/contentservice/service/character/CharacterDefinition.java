package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.CharacterVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.CharacterNotFoundException;
import dev.onepieceapi.contentservice.service.validation.CharacterValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** The character, as the generic workflow sees it. */
@Component
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public class CharacterDefinition implements ContentDefinition<Character, CharacterVersionEntity> {

	private final CharacterVersionRepository repository;

	private final CharacterValidator validator;

	private final CharacterRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.CHARACTER;
	}

	@Override
	public Version<Character> toDomain(CharacterVersionEntity entity) {
		return CharacterVersionMapper.toDomain(entity);
	}

	@Override
	public CharacterVersionEntity newVersion(ContentVersionEntity workflow) {
		return new CharacterVersionEntity(workflow);
	}

	@Override
	public void rewrite(CharacterVersionEntity entity, Character body, Instant now) {
		CharacterVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new CharacterNotFoundException(contentId);
	}

}
