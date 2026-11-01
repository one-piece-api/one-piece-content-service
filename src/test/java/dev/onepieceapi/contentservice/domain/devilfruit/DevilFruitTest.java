package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.discoveredAt;
import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared. */
class DevilFruitTest {

	private static final UUID PARAMECIA = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID LOGIA = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	@Test
	void theSpaceAroundATextDoesNotCountAndTheTypeIsKept() {
		var typed = DevilFruit.builder()
			.romaji("  Gomu Gomu ")
			.typeContentId(PARAMECIA)
			.translations(Map.of("it", new DevilFruitTranslation(" Gom Gom ")))
			.description(paragraph("it", "\tx\n"))
			.advantages(paragraph("it", " Elastico "))
			.disadvantages(paragraph("it", "Mare\n"))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Gomu Gomu");
		assertThat(normalized.typeContentId()).isEqualTo(PARAMECIA);
		assertThat(normalized.translations()).containsExactly(entry("it", new DevilFruitTranslation("Gom Gom")));
		assertThat(normalized.description()).containsExactly(new Paragraph(Map.of("it", "x"), StoryWindow.NONE));
		assertThat(normalized.advantages()).containsExactly(new Paragraph(Map.of("it", "Elastico"), StoryWindow.NONE));
		assertThat(normalized.disadvantages()).containsExactly(new Paragraph(Map.of("it", "Mare"), StoryWindow.NONE));
	}

	@Test
	void aBlankTextIsAMissingOneAndALanguageNothingWasWrittenInIsLeftOut() {
		Map<String, DevilFruitTranslation> translations = new HashMap<>();
		translations.put("it", new DevilFruitTranslation("Gom"));
		translations.put("en", new DevilFruitTranslation(""));

		var normalized = DevilFruit.builder().romaji("   ").translations(translations).build().normalized();

		assertThat(normalized.romaji()).isNull();
		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("it")).isEqualTo(new DevilFruitTranslation("Gom"));
		assertThat(normalized.translationIn("en")).isEqualTo(DevilFruitTranslation.NONE);
	}

	@Test
	void theNamesAreOnlyThoseWrittenAndTheLanguagesAlsoThoseOfTheParagraphs() {
		var fruit = DevilFruit.builder()
			.romaji("Gomu Gomu")
			.typeContentId(PARAMECIA)
			.translations(Map.of("it", new DevilFruitTranslation("Gom Gom")))
			.advantages(paragraph("en", "Rubber"))
			.build();

		assertThat(fruit.names()).containsExactly(entry("it", "Gom Gom"));
		assertThat(fruit.languages()).containsExactly("en", "it");
	}

	@Test
	void theTypeAndTheFirstAppearanceAreRequiredForReviewLikeTheRomajiAndBeforeTheTexts() {
		var empty = DevilFruit.builder().build();

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "type", "firstAppearance",
				"translations[en].name", "translations[it].name", "description", "advantages", "disadvantages");
	}

	@Test
	void aCompleteFruitMissesNothing() {
		var complete = complete(StoryPoint.ofChapter(UUID.randomUUID()));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void everySectionNeedsAParagraphDiscoveredAtTheFirstAppearance() {
		var chapter = StoryPoint.ofChapter(UUID.randomUUID());
		var elsewhere = discoveredAt(StoryPoint.ofChapter(UUID.randomUUID()), Map.of("it", "Pro"));

		var fruit = complete(chapter).toBuilder().advantages(elsewhere).build();

		assertThat(fruit.missingFields(List.of("it"))).containsExactly("advantages");
	}

	@Test
	void twoFruitsDifferingOnlyByTheirTypeAreDifferent() {
		var fruit = complete(StoryPoint.NONE);

		assertThat(fruit).isEqualTo(complete(StoryPoint.NONE))
			.isNotEqualTo(fruit.toBuilder().typeContentId(LOGIA).build());
	}

	@Test
	void theFirstAppearanceIsKeptByNormalizingAndByAnotherImageAndTellsFruitsApart() {
		var chapter = StoryPoint.ofChapter(UUID.randomUUID());
		var fruit = complete(chapter).toBuilder().romaji(" Gomu ").build();

		assertThat(fruit.normalized().firstAppearance()).isEqualTo(chapter);
		assertThat(fruit.withImage("image").firstAppearance()).isEqualTo(chapter);
		assertThat(fruit.firstAppearanceChapterId()).isEqualTo(chapter.chapterContentId());
		assertThat(fruit).isNotEqualTo(fruit.toBuilder().firstAppearance(StoryPoint.NONE).build());
	}

	@Test
	void aFruitWithoutAFirstAppearanceLacksOneAndIsNotCompleteForReview() {
		var fruit = DevilFruit.builder()
			.romaji("Gomu")
			.typeContentId(PARAMECIA)
			.translations(Map.of("it", new DevilFruitTranslation("Gom")))
			.description(paragraph("it", "Elastico"))
			.advantages(paragraph("it", "Pro"))
			.disadvantages(paragraph("it", "Contro"))
			.build();

		assertThat(fruit.firstAppearance()).isEqualTo(StoryPoint.NONE);
		assertThat(fruit.lacksFirstAppearance()).isTrue();
		assertThat(fruit.missingFields(List.of("it"))).startsWith("firstAppearance");
	}

	@Test
	void aFirstAppearanceInAnEpisodeOnlyIsEnoughForReview() {
		var episode = StoryPoint.ofEpisode(UUID.randomUUID());
		var fruit = complete(episode);

		assertThat(fruit.lacksFirstAppearance()).isFalse();
		assertThat(fruit.firstAppearanceEpisodeId()).isEqualTo(episode.episodeContentId());
		assertThat(fruit.firstAppearanceChapterId()).isNull();
		assertThat(fruit.missingFields(List.of("it"))).isEmpty();
	}

	/** A Paramecia written in Italian, each section opened at its first appearance. */
	private static DevilFruit complete(StoryPoint firstAppearance) {
		return DevilFruit.builder()
			.romaji("Gomu Gomu")
			.typeContentId(PARAMECIA)
			.translations(Map.of("it", new DevilFruitTranslation("Gom")))
			.firstAppearance(firstAppearance)
			.description(discoveredAt(firstAppearance, Map.of("it", "Elastico")))
			.advantages(discoveredAt(firstAppearance, Map.of("it", "Pro")))
			.disadvantages(discoveredAt(firstAppearance, Map.of("it", "Contro")))
			.build();
	}

}
