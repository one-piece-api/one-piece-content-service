package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterEpithetEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID CHAPTER_ID = UUID.randomUUID();

	private static final UUID EPISODE_ID = UUID.randomUUID();

	private static final UUID NINGEN = UUID.randomUUID();

	private static final UUID GYOJIN = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-21T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysWithEveryListFromItsOwn() {
		var entity = draft();
		var body = nami();

		CharacterVersionMapper.rewrite(entity, body, UPDATED);
		var version = CharacterVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body()).isEqualTo(body);
		assertThat(entity.getBirthdayMonth()).isEqualTo((short) 7);
		assertThat(entity.getBirthdayDay()).isEqualTo((short) 3);
		assertThat(entity.naturalKeyText()).isEqualTo("Nami");
	}

	@Test
	void rewritingReplacesWhatTheVersionSaidKeepingTheRowOfEachEpithetStillThere() {
		var entity = draft();
		CharacterVersionMapper.rewrite(entity, nami(), CREATED);
		CharacterEpithetEntity first = entity.getEpithets().getFirst();

		CharacterVersionMapper.rewrite(entity,
				Character.builder().epithets(List.of(new Epithet(Map.of("en", "Navigator")))).build(), UPDATED);

		assertThat(entity.getRomaji()).isNull();
		assertThat(entity.getBirthdayMonth()).isNull();
		assertThat(entity.getBirthdayDay()).isNull();
		assertThat(entity.getImageId()).isNull();
		assertThat(entity.getFirstAppearanceChapterId()).isNull();
		assertThat(entity.getAliases()).isEmpty();
		assertThat(entity.getSpeciesIds()).isEmpty();
		assertThat(entity.getEpithets()).containsExactly(first);
		assertThat(first.getText()).containsOnlyKeys("en");
	}

	private static Character nami() {
		return Character.builder()
			.romaji("Nami")
			.japaneseName("ナミ")
			.translations(Map.of("it", new CharacterTranslation("Nami", "Navigatrice"), "en",
					new CharacterTranslation("Nami", "Navigator")))
			.status(CharacterStatus.ALIVE)
			.gender(Gender.FEMALE)
			.birthday(MonthDay.of(7, 3))
			.epithets(List.of(new Epithet(Map.of("it", "Gatta ladra", "en", "Cat Burglar")),
					new Epithet(Map.of("it", "Strega del tempo"))))
			.aliases(List.of("Miss Nami", "Nami-swan"))
			.speciesIds(List.of(GYOJIN, NINGEN))
			.imageId("abc")
			.firstAppearance(new StoryPoint(CHAPTER_ID, EPISODE_ID))
			.build();
	}

	private static CharacterVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new CharacterVersionEntity(workflow);
	}

}
