package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.character.Alias;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.character.MarineRank;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import dev.onepieceapi.contentservice.domain.character.SpeciesEntry;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterEpithetEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterMembershipEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterMembershipPeriodEntity;
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

	private static final UUID LATER_CHAPTER_ID = UUID.randomUUID();

	private static final UUID EPISODE_ID = UUID.randomUUID();

	private static final UUID LATER_EPISODE_ID = UUID.randomUUID();

	private static final UUID NINGEN = UUID.randomUUID();

	private static final UUID GYOJIN = UUID.randomUUID();

	private static final UUID KAIGUN = UUID.randomUUID();

	private static final UUID MERA_MERA = UUID.randomUUID();

	private static final UUID GARP = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-21T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private static final StoryWindow OPEN = StoryWindow.from(new StoryPoint(CHAPTER_ID, EPISODE_ID));

	/** Every column of a window filled. */
	private static final StoryWindow CLOSED = new StoryWindow(new StoryPoint(CHAPTER_ID, EPISODE_ID),
			new StoryPoint(LATER_CHAPTER_ID, LATER_EPISODE_ID));

	/** Only some columns filled. */
	private static final StoryWindow PARTIAL = new StoryWindow(StoryPoint.ofChapter(CHAPTER_ID),
			StoryPoint.ofChapter(LATER_CHAPTER_ID));

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysWithEveryListAndWindowFromItsOwn() {
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
	void aWindowWithNothingChosenIsStoredAsNoColumnsAndReadBackAsNone() {
		var entity = draft();
		var body = Character.builder().aliases(List.of(new Alias("Miss Nami", StoryWindow.NONE))).build();

		CharacterVersionMapper.rewrite(entity, body, UPDATED);

		assertThat(entity.getAliases().getFirst().getWindow()).isNull();
		assertThat(CharacterVersionMapper.toDomain(entity).body().aliases())
			.containsExactly(new Alias("Miss Nami", StoryWindow.NONE));
	}

	@Test
	void rewritingReplacesWhatTheVersionSaidKeepingTheRowOfEachEpithetMembershipAndPeriodStillThere() {
		var entity = draft();
		CharacterVersionMapper.rewrite(entity, nami(), CREATED);
		CharacterEpithetEntity first = entity.getEpithets().getFirst();
		CharacterMembershipEntity membership = entity.getAffiliations().getFirst();
		CharacterMembershipPeriodEntity period = membership.getPeriods().getFirst();

		CharacterVersionMapper.rewrite(entity,
				Character.builder()
					.epithets(List.of(new Epithet(Map.of("en", "Navigator"), OPEN)))
					.affiliations(List.of(new Membership(GYOJIN, List.of(new MembershipPeriod(OPEN, null, null)))))
					.build(),
				UPDATED);

		assertThat(entity.getRomaji()).isNull();
		assertThat(entity.getBirthdayMonth()).isNull();
		assertThat(entity.getBirthdayDay()).isNull();
		assertThat(entity.getImageId()).isNull();
		assertThat(entity.getFirstAppearanceChapterId()).isNull();
		assertThat(entity.getAliases()).isEmpty();
		assertThat(entity.getSpecies()).isEmpty();
		assertThat(entity.getDevilFruits()).isEmpty();
		assertThat(entity.getEpithets()).containsExactly(first);
		assertThat(first.getText()).containsOnlyKeys("en");
		assertThat(entity.getAffiliations()).containsExactly(membership);
		assertThat(membership.getAffiliationContentId()).isEqualTo(GYOJIN);
		assertThat(membership.getPeriods()).containsExactly(period);
		assertThat(period.getRole()).isEmpty();
		assertThat(period.getMarineRank()).isNull();
	}

	private static Character nami() {
		return Character.builder()
			.romaji("Nami")
			.japaneseName("ナミ")
			.translations(Map.of("it", new CharacterTranslation("Nami"), "en", new CharacterTranslation("Nami")))
			.description(List.of(new Paragraph(Map.of("it", "Navigatrice", "en", "Navigator"), OPEN),
					new Paragraph(Map.of("it", "Ladra"), CLOSED)))
			.status(CharacterStatus.ALIVE)
			.gender(Gender.FEMALE)
			.birthday(MonthDay.of(7, 3))
			.epithets(List.of(new Epithet(Map.of("it", "Gatta ladra", "en", "Cat Burglar"), OPEN),
					new Epithet(Map.of("it", "Strega del tempo"), PARTIAL)))
			.aliases(List.of(new Alias("Miss Nami", CLOSED), new Alias("Nami-swan", OPEN)))
			.species(List.of(new SpeciesEntry(GYOJIN, PARTIAL), new SpeciesEntry(NINGEN, OPEN)))
			.affiliations(List.of(new Membership(KAIGUN,
					List.of(new MembershipPeriod(CLOSED, Map.of("it", "Recluta", "en", "Recruit"),
							MarineRank.CHORE_BOY), new MembershipPeriod(OPEN, Map.of(), MarineRank.CAPTAIN)))))
			.devilFruits(List.of(new FruitUse(MERA_MERA, CLOSED)))
			.relatives(List.of(new Relative(GARP, RelativeKind.GRANDPARENT, PARTIAL)))
			.imageId("abc")
			.firstAppearance(new StoryPoint(CHAPTER_ID, EPISODE_ID))
			.build();
	}

	private static CharacterVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new CharacterVersionEntity(workflow);
	}

}
