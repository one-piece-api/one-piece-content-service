package dev.onepieceapi.contentservice.domain.story;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared, and what is missing. */
class ChapterTest {

	private static final UUID ROMANCE_DAWN = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID VOLUME_ONE = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	@Test
	void theSpaceAroundTheTextsDoesNotCountAndTheLinksAndTheNumbersAreKept() {
		var typed = Chapter.builder()
			.number(1)
			.arcContentId(ROMANCE_DAWN)
			.volumeContentId(VOLUME_ONE)
			.japaneseTitle("  ROMANCE DAWN  ")
			.pageCount(54)
			.translations(Map.of("it", new StoryTranslation(" L'alba ", "\tInizio\n")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.number()).isEqualTo(1);
		assertThat(normalized.arcContentId()).isEqualTo(ROMANCE_DAWN);
		assertThat(normalized.volumeContentId()).isEqualTo(VOLUME_ONE);
		assertThat(normalized.pageCount()).isEqualTo(54);
		assertThat(normalized.japaneseTitle()).isEqualTo("ROMANCE DAWN");
		assertThat(normalized.translations()).containsExactly(entry("it", new StoryTranslation("L'alba", "Inizio")));
	}

	@Test
	void aBlankJapaneseTitleIsNone() {
		assertThat(Chapter.builder().japaneseTitle("  ").build().normalized().japaneseTitle()).isNull();
	}

	@Test
	void theCountryIsInCapitalsAndTheReleasesAreOrderedByDate() {
		var typed = Chapter.builder()
			.releases(List.of(new Release(" it ", LocalDate.of(2001, 5, 1)),
					new Release("jp", LocalDate.of(1997, 7, 22)), new Release("US", LocalDate.of(2003, 6, 1))))
			.build();

		assertThat(typed.normalized().releases()).containsExactly(new Release("JP", LocalDate.of(1997, 7, 22)),
				new Release("IT", LocalDate.of(2001, 5, 1)), new Release("US", LocalDate.of(2003, 6, 1)));
	}

	@Test
	void nothingWrittenIsEmptyNotNull() {
		var chapter = new Chapter(null, null, null, null, null, null, null);

		assertThat(chapter.releases()).isEmpty();
		assertThat(chapter.translations()).isEmpty();
	}

	@Test
	void theNumberIsTheNaturalKeyAndTheTitlesAreTheNamesButTwoChaptersMayShareOne() {
		var chapter = Chapter.builder()
			.number(0)
			.translations(Map.of("it", new StoryTranslation("L'alba", null), "en", new StoryTranslation(null, "d")))
			.build();

		assertThat(chapter.names()).containsExactly(entry("it", "L'alba"));
		assertThat(chapter.languages()).containsExactlyInAnyOrder("it", "en");
		assertThat(chapter.naturalKey().field()).isEqualTo("number");
		assertThat(chapter.naturalKey().value()).isEqualTo(0);
		assertThat(chapter.nameField()).isEqualTo("title");
		assertThat(chapter.uniqueNames()).isFalse();
	}

	@Test
	void theNumberAndTheArcAreRequiredForReviewBeforeTheTextsAndNeverTheVolume() {
		var empty = new Chapter(null, null, null, null, null, null, null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("number", "arc", "translations[en].title",
				"translations[en].description", "translations[it].title", "translations[it].description");
	}

	@Test
	void aChapterMissesOnlyWhatIsNotWrittenAndNoExtraIsEverMissing() {
		var withoutExtras = Chapter.builder()
			.number(1)
			.arcContentId(ROMANCE_DAWN)
			.translations(Map.of("it", new StoryTranslation("L'alba", "Inizio")))
			.build();
		var withoutDescription = withoutExtras.toBuilder()
			.translations(Map.of("it", new StoryTranslation("L'alba", null)))
			.build();

		assertThat(withoutExtras.missingFields(List.of("it"))).isEmpty();
		assertThat(withoutDescription.missingFields(List.of("it"))).containsExactly("translations[it].description");
	}

}
