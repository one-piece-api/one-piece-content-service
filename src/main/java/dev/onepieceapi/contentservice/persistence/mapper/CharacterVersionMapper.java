package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.Alias;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.SpeciesEntry;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.CharacterAliasEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterEpithetEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterFields;
import dev.onepieceapi.contentservice.persistence.entity.CharacterFruitUseEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterMembershipEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterMembershipPeriodEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterRelativeEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterSpeciesEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EpithetFields;
import dev.onepieceapi.contentservice.persistence.entity.MembershipFields;
import dev.onepieceapi.contentservice.persistence.entity.MembershipPeriodFields;
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
			.aliases(entity.getAliases().stream().map(CharacterVersionMapper::toDomain).toList())
			.species(entity.getSpecies().stream().map(CharacterVersionMapper::toDomain).toList())
			.affiliations(entity.getAffiliations().stream().map(CharacterVersionMapper::toDomain).toList())
			.devilFruits(entity.getDevilFruits().stream().map(CharacterVersionMapper::toDomain).toList())
			.relatives(entity.getRelatives().stream().map(CharacterVersionMapper::toDomain).toList())
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
			.epithets(body.epithets().stream().map(CharacterVersionMapper::toFields).toList())
			.aliases(body.aliases().stream().map(CharacterVersionMapper::toEmbeddable).toList())
			.species(body.species().stream().map(CharacterVersionMapper::toEmbeddable).toList())
			.affiliations(body.affiliations().stream().map(CharacterVersionMapper::toFields).toList())
			.devilFruits(body.devilFruits().stream().map(CharacterVersionMapper::toEmbeddable).toList())
			.relatives(body.relatives().stream().map(CharacterVersionMapper::toEmbeddable).toList())
			.imageId(body.imageId())
			.firstAppearance(FirstAppearanceMapper.toIds(body.firstAppearance()))
			.build(), now);
	}

	/** Both columns or neither: the schema says so. */
	private static MonthDay birthdayOf(CharacterVersionEntity entity) {
		return entity.getBirthdayMonth() == null ? null
				: MonthDay.of(entity.getBirthdayMonth(), entity.getBirthdayDay());
	}

	private static Membership toDomain(CharacterMembershipEntity membership) {
		return new Membership(membership.getAffiliationContentId(),
				membership.getPeriods().stream().map(CharacterVersionMapper::toDomain).toList());
	}

	/** The role sorted by language code, so the same period always reads the same way. */
	private static MembershipPeriod toDomain(CharacterMembershipPeriodEntity period) {
		return new MembershipPeriod(StoryWindowMapper.toDomain(period.getWindow()), new TreeMap<>(period.getRole()),
				period.getMarineRank());
	}

	private static MembershipFields toFields(Membership membership) {
		return new MembershipFields(membership.affiliationContentId(),
				membership.periods().stream().map(CharacterVersionMapper::toFields).toList());
	}

	private static MembershipPeriodFields toFields(MembershipPeriod period) {
		return new MembershipPeriodFields(StoryWindowMapper.toEmbeddable(period.window()), period.role(),
				period.marineRank());
	}

	private static FruitUse toDomain(CharacterFruitUseEmbeddable use) {
		return new FruitUse(use.getDevilFruitContentId(), StoryWindowMapper.toDomain(use.getWindow()));
	}

	private static CharacterFruitUseEmbeddable toEmbeddable(FruitUse use) {
		return new CharacterFruitUseEmbeddable(use.devilFruitContentId(), StoryWindowMapper.toEmbeddable(use.window()));
	}

	private static Relative toDomain(CharacterRelativeEmbeddable relative) {
		return new Relative(relative.getRelativeContentId(), relative.getKind(),
				StoryWindowMapper.toDomain(relative.getWindow()));
	}

	private static CharacterRelativeEmbeddable toEmbeddable(Relative relative) {
		return new CharacterRelativeEmbeddable(relative.relativeContentId(), relative.kind(),
				StoryWindowMapper.toEmbeddable(relative.window()));
	}

	private static Alias toDomain(CharacterAliasEmbeddable alias) {
		return new Alias(alias.getName(), StoryWindowMapper.toDomain(alias.getWindow()));
	}

	private static CharacterAliasEmbeddable toEmbeddable(Alias alias) {
		return new CharacterAliasEmbeddable(alias.name(), StoryWindowMapper.toEmbeddable(alias.window()));
	}

	private static SpeciesEntry toDomain(CharacterSpeciesEmbeddable species) {
		return new SpeciesEntry(species.getSpeciesContentId(), StoryWindowMapper.toDomain(species.getWindow()));
	}

	private static CharacterSpeciesEmbeddable toEmbeddable(SpeciesEntry species) {
		return new CharacterSpeciesEmbeddable(species.speciesContentId(),
				StoryWindowMapper.toEmbeddable(species.window()));
	}

	/** Sorted by language code, so the same epithet always reads the same way. */
	private static Epithet toDomain(CharacterEpithetEntity epithet) {
		return new Epithet(new TreeMap<>(epithet.getText()), StoryWindowMapper.toDomain(epithet.getWindow()));
	}

	private static EpithetFields toFields(Epithet epithet) {
		return new EpithetFields(epithet.text(), StoryWindowMapper.toEmbeddable(epithet.window()));
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
