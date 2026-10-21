package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.CharacterNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.EpithetResponse;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From Characters to their response bodies: what a version says is mapped here, the
 * workflow around it by {@link ContentResponseMapper}. The species, the chapter and the
 * episode it points to are given already looked up, so the mapper reads no database.
 */
@UtilityClass
public class CharacterResponseMapper {

	/** A version of a Character as its caller meets it, with everything it says. */
	public VersionResponse<CharacterResponse> toVersionResponse(VersionAccess<Character> access,
			Map<UUID, SpeciesReference> species, Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access,
				character -> toResponse(character, species, storyItems, images));
	}

	/** A row of the list of Characters: the romaji and the names. */
	public ContentSummaryResponse<CharacterNamesResponse> toSummaryResponse(ContentSummary<Character> summary) {
		return ContentResponseMapper.toSummaryResponse(summary,
				character -> new CharacterNamesResponse(character.romaji(), character.names()));
	}

	/** Everything the version says, its lists in their order. */
	public CharacterResponse toResponse(Character character, Map<UUID, SpeciesReference> species,
			Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		Map<String, CharacterTranslationResponse> translations = new TreeMap<>();
		character.translations()
			.forEach((language, translation) -> translations.put(language, toResponse(translation)));
		return CharacterResponse.builder()
			.romaji(character.romaji())
			.japaneseName(character.japaneseName())
			.translations(translations)
			.status(character.status())
			.gender(character.gender())
			.birthday(character.birthday())
			.epithets(character.epithets().stream().map(CharacterResponseMapper::toResponse).toList())
			.aliases(character.aliases())
			.species(character.speciesIds().stream().map(id -> toSpeciesResponse(id, species)).toList())
			.image(images.toResponse(character.imageId()))
			.firstAppearance(FirstAppearanceMapper.toResponse(character.firstAppearance(), storyItems))
			.build();
	}

	/**
	 * The species with its names; with only its id when it has nothing approved to be
	 * called by, which the link makes impossible but a read must not fail for.
	 */
	private static SpeciesReferenceResponse toSpeciesResponse(UUID speciesContentId,
			Map<UUID, SpeciesReference> species) {
		SpeciesReference reference = species.get(speciesContentId);
		return reference == null ? new SpeciesReferenceResponse(speciesContentId, null, Map.of())
				: SpeciesResponseMapper.toResponse(reference);
	}

	private static CharacterTranslationResponse toResponse(CharacterTranslation translation) {
		return new CharacterTranslationResponse(translation.name(), translation.description());
	}

	/** Sorted by language code, so the same epithet always reads the same way. */
	private static EpithetResponse toResponse(Epithet epithet) {
		return new EpithetResponse(new TreeMap<>(epithet.text()));
	}

}
