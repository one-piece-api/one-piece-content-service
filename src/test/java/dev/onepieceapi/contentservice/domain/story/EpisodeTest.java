package dev.onepieceapi.contentservice.domain.story;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared, and what is missing. */
class EpisodeTest {

	private static final UUID ROMANCE_DAWN = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	@Test
	void theSpaceAroundTheTextsDoesNotCountAndTheArcAndTheNumberAreKept() {
		var typed = Episode.builder()
			.number(1)
			.arcContentId(ROMANCE_DAWN)
			.japaneseTitle("\tI'm Luffy!\n")
			.translations(Map.of("it", new StoryTranslation(" Sono Rufy! ", "  Inizio ")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.number()).isEqualTo(1);
		assertThat(normalized.arcContentId()).isEqualTo(ROMANCE_DAWN);
		assertThat(normalized.japaneseTitle()).isEqualTo("I'm Luffy!");
		assertThat(normalized.translations())
			.containsExactly(entry("it", new StoryTranslation("Sono Rufy!", "Inizio")));
	}

	@Test
	void theCountryIsInCapitalsAndTheBroadcastsAreOrderedByDate() {
		var typed = Episode.builder()
			.broadcasts(List.of(new Release("it", LocalDate.of(2001, 5, 1)),
					new Release(" jp ", LocalDate.of(1999, 10, 20))))
			.build();

		assertThat(typed.normalized().broadcasts()).containsExactly(new Release("JP", LocalDate.of(1999, 10, 20)),
				new Release("IT", LocalDate.of(2001, 5, 1)));
	}

	@Test
	void theNumberIsTheNaturalKeyAndTheTitlesAreTheNamesButTwoEpisodesMayShareOne() {
		var episode = Episode.builder()
			.number(1)
			.translations(Map.of("it", new StoryTranslation("Sono Rufy!", null), "en", new StoryTranslation(null, "d")))
			.build();

		assertThat(episode.names()).containsExactly(entry("it", "Sono Rufy!"));
		assertThat(episode.naturalKey().value()).isEqualTo(1);
		assertThat(episode.uniqueNames()).isFalse();
		assertThat(new Episode(null, null, null, null, null).broadcasts()).isEmpty();
	}

	@Test
	void theNumberIsRequiredForReviewBeforeTheTextsButNeverTheArc() {
		var empty = new Episode(null, null, null, null, null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("number", "translations[en].title",
				"translations[en].description", "translations[it].title", "translations[it].description");
	}

	@Test
	void anEpisodeWithoutAnArcOrAnyExtraIsComplete() {
		var filler = Episode.builder()
			.number(54)
			.translations(Map.of("it", new StoryTranslation("Il riempitivo", "Fuori dall'arco")))
			.build();

		assertThat(filler.missingFields(List.of("it"))).isEmpty();
	}

}
