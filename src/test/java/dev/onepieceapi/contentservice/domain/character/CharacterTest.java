package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;

import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * How what an editor typed becomes what is stored and compared, and what a character
 * needs to be submitted.
 */
class CharacterTest {

	private static final UUID CHAPTER = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID KAIGUN = UUID.fromString("a1b2c3d4-0000-4000-8000-00000000a001");

	private static final UUID KAIGUN2 = UUID.fromString("a1b2c3d4-0000-4000-8000-00000000a002");

	private static final UUID NINGEN = UUID.fromString("7a1d3c2b-5e4f-4a6b-8c9d-0e1f2a3b4c5d");

	@Test
	void theKeyIsTheRomajiAndTheNamesAreUniquePerLanguage() {
		var nami = Character.builder()
			.romaji("Nami")
			.translations(Map.of("it", new CharacterTranslation("Nami", null), "en",
					new CharacterTranslation(null, "The navigator")))
			.epithets(List.of(new Epithet(Map.of("fr", "Chatte voleuse"))))
			.build();

		assertThat(nami.naturalKey().field()).isEqualTo("romaji");
		assertThat(nami.naturalKeyText()).isEqualTo("Nami");
		assertThat(nami.names()).containsExactly(entry("it", "Nami"));
		assertThat(nami.uniqueNames()).isTrue();
		assertThat(nami.languages()).containsExactly("en", "fr", "it");
	}

	@Test
	void noListIsEverNullAndNothingChosenIsNoFirstAppearance() {
		var empty = Character.builder().build();

		assertThat(empty.translations()).isEmpty();
		assertThat(empty.epithets()).isEmpty();
		assertThat(empty.aliases()).isEmpty();
		assertThat(empty.speciesIds()).isEmpty();
		assertThat(empty.firstAppearance()).isEqualTo(StoryPoint.NONE);
	}

	@Test
	void theSpaceAroundATextDoesNotCountAndABlankOneIsNotWritten() {
		var typed = Character.builder()
			.romaji("  Nami ")
			.japaneseName(" ナミ ")
			.translations(Map.of("it", new CharacterTranslation(" Nami ", "\n Navigatrice "), "en",
					new CharacterTranslation(" ", null)))
			.epithets(List.of(new Epithet(Map.of("it", " Gatta ladra ", "en", "  "))))
			.aliases(List.of(" Miss All Sunday ", "  "))
			.speciesIds(List.of(NINGEN))
			.imageId("img")
			.firstAppearance(StoryPoint.ofChapter(CHAPTER))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Nami");
		assertThat(normalized.japaneseName()).isEqualTo("ナミ");
		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("it")).isEqualTo(new CharacterTranslation("Nami", "Navigatrice"));
		assertThat(normalized.epithets()).containsExactly(new Epithet(Map.of("it", "Gatta ladra")));
		assertThat(normalized.aliases()).containsExactly("Miss All Sunday", "");
		assertThat(normalized.speciesIds()).containsExactly(NINGEN);
		assertThat(normalized.imageId()).isEqualTo("img");
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER));
		assertThat(Character.builder().romaji("   ").build().normalized().romaji()).isNull();
	}

	@Test
	void anEpithetWrittenInNoLanguageIsEmpty() {
		assertThat(new Epithet(Map.of("it", " ")).normalized().isEmpty()).isTrue();
		assertThat(new Epithet(null).isEmpty()).isTrue();
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrder() {
		var empty = Character.builder().epithets(List.of(new Epithet(Map.of("it", "Gatta ladra")))).build();

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "status", "gender",
				"firstAppearance", "translations[en].name", "translations[en].description", "translations[it].name",
				"translations[it].description", "epithets[0].text[en]");
	}

	@Test
	void theJapaneseNameTheBirthdayTheAliasesTheSpeciesAndTheImageAreNeverMissing() {
		var complete = Character.builder()
			.romaji("Nami")
			.translations(Map.of("it", new CharacterTranslation("Nami", "Navigatrice")))
			.status(CharacterStatus.ALIVE)
			.gender(Gender.FEMALE)
			.firstAppearance(StoryPoint.ofEpisode(CHAPTER))
			.build();

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void anotherBirthdayListOrderOrImageMakesAnotherVersion() {
		var base = Character.builder().romaji("Nami").aliases(List.of("A", "B")).build();

		assertThat(base).isEqualTo(Character.builder().romaji("Nami").aliases(List.of("A", "B")).build())
			.isNotEqualTo(base.toBuilder().aliases(List.of("B", "A")).build())
			.isNotEqualTo(base.toBuilder().birthday(MonthDay.of(7, 3)).build())
			.isNotEqualTo(base.withImage("img"));
	}

	@Test
	void aRoleWrittenInSomeLanguageIsRequiredInTheOthersButNoRoleIsNeverMissing() {
		var withRoles = Character.builder()
			.romaji("Nami")
			.translations(Map.of("it", new CharacterTranslation("Nami", "Navigatrice"), "en",
					new CharacterTranslation("Nami", "Navigator")))
			.status(CharacterStatus.ALIVE)
			.gender(Gender.FEMALE)
			.firstAppearance(StoryPoint.ofChapter(CHAPTER))
			.affiliations(List.of(new Membership(KAIGUN, Map.of(), null, false),
					new Membership(KAIGUN2, Map.of("it", "Navigatrice"), null, false)))
			.build();

		assertThat(withRoles.missingFields(List.of("en", "it"))).containsExactly("affiliations[1].role[en]");
	}

	@Test
	void theSpaceAroundARoleDoesNotCountAndTheLanguagesOfRolesAreThoseInUse() {
		var typed = Character.builder()
			.affiliations(
					List.of(new Membership(KAIGUN, Map.of("fr", " Capitaine ", "it", "  "), MarineRank.CAPTAIN, true)))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.affiliations())
			.containsExactly(new Membership(KAIGUN, Map.of("fr", "Capitaine"), MarineRank.CAPTAIN, true));
		assertThat(typed.languages()).containsExactly("fr", "it");
		assertThat(Character.builder().build().affiliations()).isEmpty();
	}

	@Test
	void theOrderOfTheMembershipsOrAFormerFlagMakesAnotherVersion() {
		var current = new Membership(KAIGUN, Map.of(), null, false);
		var other = new Membership(KAIGUN2, Map.of(), null, false);
		var base = Character.builder().romaji("Nami").affiliations(List.of(current, other)).build();

		assertThat(base).isNotEqualTo(base.toBuilder().affiliations(List.of(other, current)).build())
			.isNotEqualTo(base.toBuilder()
				.affiliations(List.of(new Membership(KAIGUN, Map.of(), null, true), other))
				.build());
	}

	@Test
	void theRanksAreInTheOrderOfTheHierarchyHighestFirst() {
		assertThat(MarineRank.values()).hasSize(19);
		assertThat(MarineRank.values()[0]).isEqualTo(MarineRank.FLEET_ADMIRAL);
		assertThat(MarineRank.ADMIRAL.compareTo(MarineRank.COMMODORE)).isNegative();
		assertThat(MarineRank.values()[18]).isEqualTo(MarineRank.CHORE_BOY);
	}

}
