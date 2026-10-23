package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.Relative;
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
 * @param devilFruits the fruits, in order
 * @param relatives the relatives, in order
 */
@Builder
public record CharacterFields(String romaji, String japaneseName,
		Map<String, CharacterTranslationEmbeddable> translations, CharacterStatus status, Gender gender,
		Short birthdayMonth, Short birthdayDay, List<Map<String, String>> epithets, List<String> aliases,
		List<UUID> speciesIds, List<Membership> affiliations, List<FruitUse> devilFruits, List<Relative> relatives,
		String imageId, FirstAppearanceIds firstAppearance) {

}
