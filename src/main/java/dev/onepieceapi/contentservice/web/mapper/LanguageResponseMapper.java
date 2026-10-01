package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.web.dto.response.LanguageResponse;
import lombok.experimental.UtilityClass;

/** From the domain to the response bodies of the language catalog endpoints. */
@UtilityClass
public class LanguageResponseMapper {

	public LanguageResponse toResponse(Language language) {
		return new LanguageResponse(language.code(), language.name());
	}

}
