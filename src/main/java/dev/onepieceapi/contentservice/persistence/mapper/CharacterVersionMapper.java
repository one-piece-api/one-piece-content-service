package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.CharacterEpithetEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterFields;
import dev.onepieceapi.contentservice.persistence.entity.CharacterTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of a Character version - its workflow in the shared table, what it
 * says in the entity's own and in its lists - and the domain.
 */
@UtilityClass
public class CharacterVersionMapper {

	public Version<Character> toDomain(CharacterVersionEntity entity) {
		var body = Character.builder()
			.romaji(entity.getRomaji())
			.japaneseName(entity.getJapaneseName())
			.translations(toDomain(entity.getTranslations()))
			.status(entity.getStatus())
			.gender(entity.getGender())
			.birthday(birthdayOf(entity))
			.epithets(entity.getEpithets().stream().map(CharacterVersionMapper::toDomain).toList())
			.aliases(entity.getAliases())
			.speciesIds(entity.getSpeciesIds())
			.imageId(entity.getImageId())
			.firstAppearance(new StoryPoint(entity.getFirstAppearanceChapterId(), entity.getFirstAppearanceEpisodeId()))
			.build();
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(CharacterVersionEntity entity, Character body, Instant now) {
		MonthDay birthday = body.birthday();
		entity.rewrite(CharacterFields.builder()
			.romaji(body.romaji())
			.japaneseName(body.japaneseName())
			.translations(toEmbeddables(body.translations()))
			.status(body.status())
			.gender(body.gender())
			.birthdayMonth(birthday == null ? null : (short) birthday.getMonthValue())
			.birthdayDay(birthday == null ? null : (short) birthday.getDayOfMonth())
			.epithets(body.epithets().stream().map(Epithet::text).toList())
			.aliases(body.aliases())
			.speciesIds(body.speciesIds())
			.imageId(body.imageId())
			.firstAppearance(FirstAppearanceMapper.toIds(body.firstAppearance()))
			.build(), now);
	}

	/** Both columns or neither: the schema says so. */
	private static MonthDay birthdayOf(CharacterVersionEntity entity) {
		return entity.getBirthdayMonth() == null ? null
				: MonthDay.of(entity.getBirthdayMonth(), entity.getBirthdayDay());
	}

	/** Sorted by language code, so the same epithet always reads the same way. */
	private static Epithet toDomain(CharacterEpithetEntity epithet) {
		return new Epithet(new TreeMap<>(epithet.getText()));
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, CharacterTranslation> toDomain(
			Map<String, CharacterTranslationEmbeddable> translations) {
		Map<String, CharacterTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new CharacterTranslation(translation.getName(), translation.getDescription())));
		return byLanguage;
	}

	private static Map<String, CharacterTranslationEmbeddable> toEmbeddables(
			Map<String, CharacterTranslation> translations) {
		Map<String, CharacterTranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new CharacterTranslationEmbeddable(translation.name(), translation.description())));
		return byLanguage;
	}

}
