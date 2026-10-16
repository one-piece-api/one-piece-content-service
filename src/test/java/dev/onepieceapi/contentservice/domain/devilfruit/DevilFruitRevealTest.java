package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.INITIAL_KNOWLEDGE;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.NIKA;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.PARAMECIA;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.RUBBER_BODY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The initial knowledge and the reveals as part of a Devil Fruit's version (T13;
 * ADR-0010): how what an editor typed in them is stored and compared, which languages
 * they bring, and what they owe at submission.
 */
class DevilFruitRevealTest {

	private static DevilFruit with(DevilFruitBelief initialKnowledge, DevilFruitReveal... reveals) {
		return new DevilFruit("Hito Hito no Mi", GomuGomuReveals.ZOAN, null,
				Map.of("it", GomuGomuReveals.TRUTH_IT, "en", GomuGomuReveals.TRUTH_EN), null, new StoryPoint(CHAPTER_1),
				initialKnowledge, List.of(reveals));
	}

	@Test
	void theInitialKnowledgeAndTheRevealsAreNormalizedLikeTheFruit() {
		Map<String, DevilFruitTranslation> typed = new HashMap<>();
		typed.put("it", new DevilFruitTranslation(" Gom Gom ", "  ", null, null));
		typed.put("en", new DevilFruitTranslation("", null, null, " "));
		var reveal = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), Set.of(DevilFruitField.NAME),
				new DevilFruitBelief("  Gomu Gomu ", PARAMECIA, null, typed),
				new HashMap<>(Map.of("it", " Il frutto è Nika ", "en", "  ")));

		var normalized = with(new DevilFruitBelief(" ", null, null, typed), reveal).normalized();

		DevilFruitReveal first = normalized.reveals().getFirst();
		assertThat(first.values().romaji()).isEqualTo("Gomu Gomu");
		assertThat(first.values().typeContentId()).isEqualTo(PARAMECIA);
		assertThat(first.values().translations())
			.containsExactly(entry("it", new DevilFruitTranslation("Gom Gom", null, null, null)));
		assertThat(first.titles()).containsExactly(entry("it", "Il frutto è Nika"));
		assertThat(first.fields()).containsExactly(DevilFruitField.NAME);
		assertThat(normalized.initialKnowledge().romaji()).isNull();
		assertThat(normalized.initialKnowledge().translations()).containsOnlyKeys("it");
	}

	@Test
	void aRevealNamingNoFieldDefinesNothingWhateverItsTitleOrValues() {
		var titled = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), Set.of(), RUBBER_BODY.values(),
				Map.of("it", "Il frutto è Nika"));

		assertThat(titled.definesNothing()).isTrue();
		assertThat(NIKA.definesNothing()).isFalse();
	}

	@Test
	void nothingChosenIsNone() {
		var reveal = new DevilFruitReveal(null, null, null, null);
		var fruit = new DevilFruit("Gomu", null, null, Map.of(), null, null, null, null);

		assertThat(reveal.revealedAt()).isEqualTo(StoryPoint.NONE);
		assertThat(reveal.fields()).isEmpty();
		assertThat(reveal.values()).isEqualTo(DevilFruitBelief.NONE);
		assertThat(fruit.reveals()).isEmpty();
		assertThat(fruit.initialKnowledge()).isEqualTo(DevilFruitBelief.NONE);
	}

	@Test
	void theLanguagesAreThoseOfTheFruitItsInitialKnowledgeAndItsReveals() {
		var french = new DevilFruitBelief(null, null, null,
				Map.of("fr", new DevilFruitTranslation("Fruit du Caoutchouc", null, null, null)));
		var titled = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), Set.of(DevilFruitField.NAME), null,
				Map.of("de", "Titel"));

		assertThat(with(french).languages()).containsExactly("en", "fr", "it");
		assertThat(with(DevilFruitBelief.NONE, titled).languages()).containsExactly("de", "en", "it");
		assertThat(with(french).names()).containsOnlyKeys("it", "en");
	}

	@Test
	void everyValueBelievedMustBeWrittenInEveryLanguageForReview() {
		var italianOnly = new DevilFruitBelief("Gomu Gomu no Mi", PARAMECIA, null,
				Map.of("it", new DevilFruitTranslation("Frutto Gom Gom", "Gomma", null, null)));

		assertThat(with(italianOnly, RUBBER_BODY, NIKA).missingFields(List.of("en", "it")))
			.containsExactly("initialKnowledge.translations[en].name", "initialKnowledge.translations[en].description");
		assertThat(with(INITIAL_KNOWLEDGE, RUBBER_BODY, NIKA).missingFields(List.of("en", "it"))).isEmpty();
	}

	@Test
	void everyRevealMustTitleItsRevealInEveryLanguageForReviewButNotOnSave() {
		var untitled = new DevilFruitReveal(NIKA.revealedAt(), NIKA.fields(), null, Map.of("it", "Il frutto è Nika"));

		assertThat(with(INITIAL_KNOWLEDGE, RUBBER_BODY, untitled).missingFields(List.of("en", "it")))
			.containsExactly("reveals[1].titles[en]");
	}

	@Test
	void twoVersionsDifferingOnlyByTheirInitialKnowledgeOrRevealsAreDifferent() {
		var otherFields = new DevilFruitReveal(NIKA.revealedAt(), Set.of(DevilFruitField.NAME), null, NIKA.titles());
		var retitled = new DevilFruitReveal(NIKA.revealedAt(), NIKA.fields(), null, Map.of("it", "Altro"));

		assertThat(with(INITIAL_KNOWLEDGE, NIKA)).isNotEqualTo(with(INITIAL_KNOWLEDGE, otherFields))
			.isNotEqualTo(with(INITIAL_KNOWLEDGE, retitled))
			.isNotEqualTo(with(DevilFruitBelief.NONE, NIKA))
			.isEqualTo(with(INITIAL_KNOWLEDGE, NIKA));
	}

	@Test
	void anotherImageKeepsTheInitialKnowledgeAndTheReveals() {
		var fruit = with(INITIAL_KNOWLEDGE, NIKA).withImage("image");

		assertThat(fruit.reveals()).containsExactly(NIKA);
		assertThat(fruit.initialKnowledge()).isEqualTo(INITIAL_KNOWLEDGE);
	}

}
