package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.Gender;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Everything a character version says, as its row is written - what
 * {@link CharacterVersionEntity#rewrite} replaces it with.
 *
 * @param birthdayMonth null together with {@code birthdayDay} for no birthday
 * @param epithets in order
 * @param aliases in order
 * @param species in order
 * @param affiliations the memberships, in order
 * @param devilFruits the fruits, in order
 * @param relatives the relatives, in order
 * @param gallery its images, in order
 * @param paragraphs every section, in section order
 */
@Builder
public record CharacterFields(String romaji, String japaneseName,
		Map<String, CharacterTranslationEmbeddable> translations, CharacterStatus status, Gender gender,
		Short birthdayMonth, Short birthdayDay, List<EpithetFields> epithets, List<CharacterAliasEmbeddable> aliases,
		List<CharacterSpeciesEmbeddable> species, List<MembershipFields> affiliations,
		List<CharacterFruitUseEmbeddable> devilFruits, List<CharacterRelativeEmbeddable> relatives,
		List<GalleryImageFields> gallery, FirstAppearanceIds firstAppearance, List<ParagraphFields> paragraphs,
		String initialRomaji, String initialJapaneseName, CharacterStatus initialStatus,
		Map<String, CharacterTranslationEmbeddable> initialTranslations, List<CharacterRevealEntity.Written> reveals) {

}
