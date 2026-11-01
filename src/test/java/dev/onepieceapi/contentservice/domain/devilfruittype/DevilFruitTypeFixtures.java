package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;

import java.util.Map;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.discoveredAt;

/** Devil Fruit Types for tests. */
public final class DevilFruitTypeFixtures {

	private DevilFruitTypeFixtures() {
	}

	/**
	 * Complete in both languages of the catalog, without subcategories, every section
	 * opened at its first appearance.
	 */
	public static DevilFruitType complete(String romaji, StoryPoint firstAppearance) {
		return DevilFruitType.builder()
			.romaji(romaji)
			.translations(Map.of("it", new DevilFruitTypeTranslation(romaji + " IT"), "en",
					new DevilFruitTypeTranslation(romaji + " EN")))
			.firstAppearance(firstAppearance)
			.description(discoveredAt(firstAppearance, Map.of("it", "descrizione", "en", "description")))
			.advantages(discoveredAt(firstAppearance, Map.of("it", "vantaggi", "en", "advantages")))
			.disadvantages(discoveredAt(firstAppearance, Map.of("it", "svantaggi", "en", "disadvantages")))
			.build();
	}

}
