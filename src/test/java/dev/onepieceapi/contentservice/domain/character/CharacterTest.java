package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.contentservice.domain.readertime.NamedWindow;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.WindowSequence;
import dev.onepieceapi.readertime.ImageKind;
import org.junit.jupiter.api.Test;

import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * How what an editor typed becomes what is stored and compared, what a character needs to
 * be submitted, and how its windows are named.
 */
class CharacterTest {

	private static final UUID CHAPTER = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID LATER_CHAPTER = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f12");

	private static final UUID EPISODE = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f21");

	private static final UUID KAIGUN = UUID.fromString("a1b2c3d4-0000-4000-8000-00000000a001");

	private static final UUID KAIGUN2 = UUID.fromString("a1b2c3d4-0000-4000-8000-00000000a002");

	private static final UUID NINGEN = UUID.fromString("7a1d3c2b-5e4f-4a6b-8c9d-0e1f2a3b4c5d");

	private static final UUID MERA_MERA = UUID.fromString("7a1d3c2b-5e4f-4a6b-8c9d-0e1f2a3b4c5e");

	/** Discovered at the first appearance, with no end. */
	private static final StoryWindow FROM_START = StoryWindow.from(StoryPoint.ofChapter(CHAPTER));

	private static final StoryWindow ENDED = new StoryWindow(StoryPoint.ofChapter(CHAPTER),
			StoryPoint.ofChapter(LATER_CHAPTER));

	@Test
	void theKeyIsTheRomajiAndTheNamesAreUniquePerLanguage() {
		var nami = Character.builder()
			.romaji("Nami")
			.translations(Map.of("it", new CharacterTranslation("Nami")))
			.description(paragraph("en", "The navigator"))
			.epithets(List.of(new Epithet(Map.of("fr", "Chatte voleuse"), FROM_START)))
			.build();

		assertThat(nami.naturalKey().field()).isEqualTo("romaji");
		assertThat(nami.naturalKeyText()).isEqualTo("Nami");
		assertThat(nami.names()).containsExactly(entry("it", "Nami"));
		assertThat(nami.uniqueNames()).isTrue();
		assertThat(nami.languages()).containsExactly("en", "fr", "it");
	}

	@Test
	void noListIsEverNullAndNothingChosenIsNoFirstAppearanceAndNoWindow() {
		var empty = Character.builder().build();

		assertThat(empty.translations()).isEmpty();
		assertThat(empty.epithets()).isEmpty();
		assertThat(empty.aliases()).isEmpty();
		assertThat(empty.species()).isEmpty();
		assertThat(empty.firstAppearance()).isEqualTo(StoryPoint.NONE);
		assertThat(new Alias("Sogeking", null).window()).isEqualTo(StoryWindow.NONE);
		assertThat(new Membership(KAIGUN, null).periods()).isEmpty();
	}

	@Test
	void theSpaceAroundATextDoesNotCountAndABlankOneIsNotWritten() {
		var typed = Character.builder()
			.romaji("  Nami ")
			.japaneseName(" ナミ ")
			.translations(Map.of("it", new CharacterTranslation(" Nami "), "en", new CharacterTranslation(" ")))
			.description(paragraph(Map.of("it", "\n Navigatrice ", "en", " ")))
			.epithets(List.of(new Epithet(Map.of("it", " Gatta ladra ", "en", "  "), FROM_START)))
			.aliases(List.of(new Alias(" Miss All Sunday ", FROM_START), new Alias("  ", null)))
			.species(List.of(new SpeciesEntry(NINGEN, FROM_START)))
			.gallery(List.of(new GalleryImage("img", ImageKind.ASPETTO, FROM_START, Map.of("it", " Look ", "en", " "))))
			.firstAppearance(StoryPoint.ofChapter(CHAPTER))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Nami");
		assertThat(normalized.japaneseName()).isEqualTo("ナミ");
		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("it")).isEqualTo(new CharacterTranslation("Nami"));
		assertThat(normalized.description())
			.containsExactly(new Paragraph(Map.of("it", "Navigatrice"), StoryWindow.NONE));
		assertThat(normalized.epithets()).containsExactly(new Epithet(Map.of("it", "Gatta ladra"), FROM_START));
		assertThat(normalized.aliases()).containsExactly(new Alias("Miss All Sunday", FROM_START), new Alias("", null));
		assertThat(normalized.species()).containsExactly(new SpeciesEntry(NINGEN, FROM_START));
		assertThat(normalized.gallery())
			.containsExactly(new GalleryImage("img", ImageKind.ASPETTO, FROM_START, Map.of("it", "Look")));
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER));
		assertThat(Character.builder().romaji("   ").build().normalized().romaji()).isNull();
	}

	@Test
	void anEpithetWrittenInNoLanguageIsEmpty() {
		assertThat(new Epithet(Map.of("it", " "), FROM_START).normalized().isEmpty()).isTrue();
		assertThat(new Epithet(null, null).isEmpty()).isTrue();
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrder() {
		var empty = Character.builder().epithets(List.of(new Epithet(Map.of("it", "Gatta ladra"), FROM_START))).build();

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "status", "gender",
				"firstAppearance", "translations[en].name", "translations[it].name", "epithets[0].text[en]",
				"description", "gallery");
	}

	@Test
	void theJapaneseNameTheBirthdayTheAliasesAndTheSpeciesAreNeverMissing() {
		var complete = complete().build();

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void everyWindowNeedsItsDiscoveredPointToBeSubmittedButNeverAnEnd() {
		var drafted = complete()
			.aliases(List.of(new Alias("Sogeking", StoryWindow.NONE), new Alias("Usopp-ojisan", ENDED)))
			.species(List.of(new SpeciesEntry(NINGEN, new StoryWindow(null, StoryPoint.ofChapter(LATER_CHAPTER)))))
			.affiliations(List.of(new Membership(KAIGUN,
					List.of(new MembershipPeriod(ENDED, null, null), new MembershipPeriod(null, null, null)))))
			.devilFruits(List.of(new FruitUse(MERA_MERA, FROM_START)))
			.build();

		assertThat(drafted.missingFields(List.of("it"))).containsExactly("aliases[0].discovered",
				"species[0].discovered", "affiliations[0].periods[1].discovered");
	}

	@Test
	void aMembershipWithoutPeriodsIsADraftsAndSubmittingNamesItsPeriods() {
		var drafted = complete().affiliations(List.of(new Membership(KAIGUN, List.of()))).build();

		assertThat(drafted.missingFields(List.of("it"))).containsExactly("affiliations[0].periods");
	}

	@Test
	void theWindowsAreNamedListByListAsARequestNamesThem() {
		var usopp = complete().epithets(List.of(new Epithet(Map.of("it", "Re dei cecchini"), FROM_START)))
			.aliases(List.of(new Alias("Sogeking", ENDED)))
			.species(List.of(new SpeciesEntry(NINGEN, FROM_START)))
			.affiliations(List.of(new Membership(KAIGUN,
					List.of(new MembershipPeriod(ENDED, null, null), new MembershipPeriod(FROM_START, null, null)))))
			.devilFruits(List.of(new FruitUse(MERA_MERA, ENDED)))
			.relatives(List.of(new Relative(KAIGUN2, RelativeKind.PARENT, FROM_START)))
			.build();

		assertThat(usopp.windows()).extracting(NamedWindow::field)
			.containsExactly("epithets[0]", "aliases[0]", "species[0]", "affiliations[0].periods[0]",
					"affiliations[0].periods[1]", "devilFruits[0]", "relatives[0]", "description[0]", "gallery[0]");
		assertThat(usopp.windowSequences())
			.containsExactly(new WindowSequence("affiliations[0].periods", List.of(ENDED, FROM_START)));
	}

	@Test
	void thePointsOfACharacterAreItsFirstAppearanceThenThoseOfItsWindowsChosen() {
		var luffy = complete().firstAppearance(new StoryPoint(CHAPTER, EPISODE))
			.aliases(List.of(new Alias("Lucy", ENDED), new Alias("Mugiwara", StoryWindow.NONE)))
			.build();

		assertThat(luffy.points()).containsExactly(new StoryPoint(CHAPTER, EPISODE), StoryPoint.ofChapter(CHAPTER),
				StoryPoint.ofChapter(LATER_CHAPTER), StoryPoint.ofChapter(CHAPTER), StoryPoint.ofChapter(CHAPTER));
	}

	@Test
	void anotherBirthdayListOrderWindowOrImageMakesAnotherVersion() {
		var base = Character.builder()
			.romaji("Nami")
			.aliases(List.of(new Alias("A", FROM_START), new Alias("B", FROM_START)))
			.build();

		assertThat(base)
			.isEqualTo(Character.builder()
				.romaji("Nami")
				.aliases(List.of(new Alias("A", FROM_START), new Alias("B", FROM_START)))
				.build())
			.isNotEqualTo(
					base.toBuilder().aliases(List.of(new Alias("B", FROM_START), new Alias("A", FROM_START))).build())
			.isNotEqualTo(base.toBuilder().aliases(List.of(new Alias("A", FROM_START), new Alias("B", ENDED))).build())
			.isNotEqualTo(base.toBuilder().birthday(MonthDay.of(7, 3)).build())
			.isNotEqualTo(base.withGallery(List.of(new GalleryImage("img", ImageKind.ASPETTO, FROM_START, null))));
	}

	@Test
	void aRoleWrittenInSomeLanguageIsRequiredInTheOthersButNoRoleIsNeverMissing() {
		var withRoles = complete()
			.translations(Map.of("it", new CharacterTranslation("Nami"), "en", new CharacterTranslation("Nami")))
			.description(List.of(new Paragraph(Map.of("it", "Navigatrice", "en", "Navigator"), FROM_START)))
			.gallery(List
				.of(new GalleryImage("img", ImageKind.ASPETTO, FROM_START, Map.of("it", "Aspetto", "en", "Look"))))
			.affiliations(List.of(new Membership(KAIGUN, List.of(new MembershipPeriod(FROM_START, Map.of(), null))),
					new Membership(KAIGUN2,
							List.of(new MembershipPeriod(ENDED, null, null),
									new MembershipPeriod(FROM_START, Map.of("it", "Navigatrice"), null)))))
			.build();

		assertThat(withRoles.missingFields(List.of("en", "it"))).containsExactly("affiliations[1].periods[1].role[en]");
	}

	@Test
	void theSpaceAroundARoleDoesNotCountAndTheLanguagesOfRolesAreThoseInUse() {
		var typed = Character.builder()
			.affiliations(List.of(new Membership(KAIGUN,
					List.of(new MembershipPeriod(ENDED, Map.of("fr", " Capitaine ", "it", "  "), MarineRank.CAPTAIN)))))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.affiliations()).containsExactly(new Membership(KAIGUN,
				List.of(new MembershipPeriod(ENDED, Map.of("fr", "Capitaine"), MarineRank.CAPTAIN))));
		assertThat(typed.languages()).containsExactly("fr", "it");
		assertThat(Character.builder().build().affiliations()).isEmpty();
	}

	@Test
	void theOrderOfTheMembershipsOrOfThePeriodsOrAnEndMakesAnotherVersion() {
		var first = new MembershipPeriod(ENDED, null, null);
		var second = new MembershipPeriod(FROM_START, null, null);
		var current = new Membership(KAIGUN, List.of(first, second));
		var other = new Membership(KAIGUN2, List.of(second));
		var base = Character.builder().romaji("Nami").affiliations(List.of(current, other)).build();

		assertThat(base).isNotEqualTo(base.toBuilder().affiliations(List.of(other, current)).build())
			.isNotEqualTo(base.toBuilder()
				.affiliations(List.of(new Membership(KAIGUN, List.of(second, first)), other))
				.build())
			.isNotEqualTo(
					base.toBuilder().affiliations(List.of(current, new Membership(KAIGUN2, List.of(first)))).build());
	}

	@Test
	void aMembershipHasARankWhenOneOfItsPeriodsHoldsOne() {
		var koby = new Membership(KAIGUN, List.of(new MembershipPeriod(ENDED, null, null),
				new MembershipPeriod(FROM_START, null, MarineRank.CAPTAIN)));

		assertThat(koby.hasRank()).isTrue();
		assertThat(new Membership(KAIGUN, List.of(new MembershipPeriod(ENDED, null, null))).hasRank()).isFalse();
	}

	@Test
	void theRanksAreInTheOrderOfTheHierarchyHighestFirst() {
		assertThat(MarineRank.values()).hasSize(19);
		assertThat(MarineRank.values()[0]).isEqualTo(MarineRank.FLEET_ADMIRAL);
		assertThat(MarineRank.ADMIRAL.compareTo(MarineRank.COMMODORE)).isNegative();
		assertThat(MarineRank.values()[18]).isEqualTo(MarineRank.CHORE_BOY);
	}

	/** Complete in Italian, its first appearance in {@link #CHAPTER}. */
	private static Character.CharacterBuilder complete() {
		return Character.builder()
			.romaji("Nami")
			.translations(Map.of("it", new CharacterTranslation("Nami")))
			.description(List.of(new Paragraph(Map.of("it", "Navigatrice"), FROM_START)))
			.status(CharacterStatus.ALIVE)
			.gender(Gender.FEMALE)
			.firstAppearance(StoryPoint.ofChapter(CHAPTER))
			.gallery(List.of(new GalleryImage("img", ImageKind.ASPETTO, FROM_START, Map.of("it", "Aspetto"))));
	}

}
