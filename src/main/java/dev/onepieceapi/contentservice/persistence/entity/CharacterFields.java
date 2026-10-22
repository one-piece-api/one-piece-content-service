package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.character.Membership;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a character version says, as its row is written - what
 * {@link CharacterVersionEntity#rewrite} replaces it with.
 *
 * @param birthdayMonth null together with {@code birthdayDay} for no birthday
 * @param epithets the text per language code of each epithet, in order
 * @param affiliations the memberships, in order
 */
@Builder
public record CharacterFields(String romaji, String japaneseName,
		Map<String, CharacterTranslationEmbeddable> translations, CharacterStatus status, Gender gender,
		Short birthdayMonth, Short birthdayDay, List<Map<String, String>> epithets, List<String> aliases,
		List<UUID> speciesIds, List<Membership> affiliations, String imageId, FirstAppearanceIds firstAppearance) {

}
