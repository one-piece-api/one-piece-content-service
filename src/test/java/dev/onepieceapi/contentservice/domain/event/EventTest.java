package dev.onepieceapi.contentservice.domain.event;

import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * How what an editor typed becomes what is stored and compared, what is missing, and
 * where the reader learns of an event.
 */
class EventTest {

	private static final UUID CHAPTER_1 = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID CHAPTER_956 = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	private static final UUID GOMU = UUID.fromString("1b2c3d4e-5f60-4718-8a9b-0c1d2e3f4a5b");

	private static final UUID MERA = UUID.fromString("0a1b2c3d-4e5f-4607-8819-2a3b4c5d6e7f");

	@Test
	void theParticipantsAreEachOnceInTheOrderOfTheirIdsAndTheTextsAreTidied() {
		var typed = Event.builder()
			.devilFruitContentIds(List.of(GOMU, MERA, GOMU))
			.translations(Map.of("it", new StoryTranslation(" Reverie ", "  ")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.devilFruitContentIds()).containsExactly(MERA, GOMU);
		assertThat(normalized.translations()).containsExactly(entry("it", new StoryTranslation("Reverie", null)));
	}

	@Test
	void nothingWrittenIsEmptyOrNoneNotNull() {
		var event = new Event(null, null, null, null, null);

		assertThat(event.revealedAt()).isEqualTo(StoryPoint.NONE);
		assertThat(event.devilFruitContentIds()).isEmpty();
		assertThat(event.translations()).isEmpty();
	}

	@Test
	void reviewNeedsWhenItHappensAndATitlePerLanguageButNeverADescription() {
		var event = Event.builder().translations(Map.of("en", new StoryTranslation("Reverie", null))).build();

		assertThat(event.missingFields(List.of("en", "it"))).containsExactly("occursAt", "translations[it].title");
	}

	@Test
	void anEventHasNoNaturalKeyAndItsTitleMayRepeat() {
		var event = Event.builder().translations(Map.of("en", new StoryTranslation("Reverie", null))).build();

		assertThat(event.naturalKey()).isEmpty();
		assertThat(event.uniqueNames()).isFalse();
		assertThat(event.names()).containsExactly(entry("en", "Reverie"));
	}

	@Test
	void theReaderLearnsOfItWhereWrittenOrElseInTheChapterItOccursIn() {
		var occurring = Event.builder().occursAt(new ChapterTime(CHAPTER_1)).build();
		var revealed = occurring.toBuilder().revealedAt(new StoryPoint(CHAPTER_956)).build();
		var beforeTheStory = Event.builder().occursAt(new WorldDateTime(new WorldDate(1500, null, false))).build();

		assertThat(occurring.effectiveRevealedAtChapterId()).contains(CHAPTER_1);
		assertThat(revealed.effectiveRevealedAtChapterId()).contains(CHAPTER_956);
		assertThat(beforeTheStory.effectiveRevealedAtChapterId()).isEmpty();
	}

}
