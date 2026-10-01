package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import lombok.experimental.UtilityClass;

/** Between a row of the language catalog and its domain {@link Language}. */
@UtilityClass
public class LanguageMapper {

	public LanguageEntity toEntity(Language language) {
		return new LanguageEntity(language.code(), language.name());
	}

	public Language toDomain(LanguageEntity entity) {
		return new Language(entity.getCode(), entity.getName());
	}

}
