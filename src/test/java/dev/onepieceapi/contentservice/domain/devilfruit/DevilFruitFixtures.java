package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;

import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.discoveredAt;

/** Devil Fruits for tests. */
public final class DevilFruitFixtures {

	private DevilFruitFixtures() {
	}

	/**
	 * Complete in both languages of the catalog, every section opened at its first
	 * appearance.
	 */
	public static DevilFruit complete(String romaji, UUID type, UUID subcategory, StoryPoint firstAppearance) {
		return DevilFruit.builder()
			.romaji(romaji)
			.typeContentId(type)
			.subcategoryId(subcategory)
			.translations(Map.of("it", new DevilFruitTranslation(romaji + " it"), "en",
					new DevilFruitTranslation(romaji + " no Mi")))
			.firstAppearance(firstAppearance)
			.description(discoveredAt(firstAppearance, Map.of("it", "Descrizione", "en", "Description")))
			.advantages(discoveredAt(firstAppearance, Map.of("it", "Pro", "en", "Pros")))
			.disadvantages(discoveredAt(firstAppearance, Map.of("it", "Contro", "en", "Cons")))
			.build();
	}

}
