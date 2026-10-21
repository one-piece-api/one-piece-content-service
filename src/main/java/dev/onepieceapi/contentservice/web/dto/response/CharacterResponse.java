package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.MonthDay;
import java.util.List;
import java.util.Map;

/**
 * What a version of a Character says - the body of its {@link VersionResponse}.
 *
 * @param translations name and description per language code
 * @param birthday day and month, ISO 8601 {@code --MM-DD}; null for none
 * @param species each species as of today, in order
 * @param image its portrait; null for none
 * @param firstAppearance the chapter and/or the episode it first appears in; null for
 * none
 */
@Builder
public record CharacterResponse(String romaji, String japaneseName,
		Map<String, CharacterTranslationResponse> translations, CharacterStatus status, Gender gender,
		@Schema(type = "string", example = "--05-05") MonthDay birthday, List<EpithetResponse> epithets,
		List<String> aliases, List<SpeciesReferenceResponse> species, ImageResponse image,
		FirstAppearanceResponse firstAppearance) {

}
