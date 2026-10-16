package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The reference case of the reveals (implementation plan of the timeline, T13; ADR-0010),
 * shared by the resolution tests here and by the public API's in TL6: the Hito Hito no
 * Mi, Model: Nika, met at chapter 1 as the Gomu Gomu no Mi, a Paramecia.
 * <ul>
 * <li>from chapter 1 the reader knows the Gomu Gomu no Mi, a Paramecia, "rubber, nothing
 * more", and nothing of its disadvantages;</li>
 * <li>at chapter 100 the reader learns of a body of rubber - the description changes
 * twice;</li>
 * <li>at chapter 1044 the reader learns the truth: a Mythical Zoan, and its
 * disadvantages.</li>
 * </ul>
 * The advantages are never revealed: the truth throughout. Chapters are named by fixed
 * ids; {@link #NUMBERS} numbers them.
 */
public final class GomuGomuReveals {

	public static final UUID ZOAN = UUID.fromString("a0000000-0000-0000-0000-00000000200a");

	public static final UUID MYTHICAL = UUID.fromString("a0000000-0000-0000-0000-00000000200b");

	public static final UUID PARAMECIA = UUID.fromString("a0000000-0000-0000-0000-00000000200c");

	public static final UUID CHAPTER_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

	public static final UUID CHAPTER_100 = UUID.fromString("c0000000-0000-0000-0000-000000000100");

	public static final UUID CHAPTER_1044 = UUID.fromString("c0000000-0000-0000-0000-000000001044");

	/** The number of each chapter above, by its content. */
	public static final Map<UUID, Integer> NUMBERS = Map.of(CHAPTER_1, 1, CHAPTER_100, 100, CHAPTER_1044, 1044);

	public static final DevilFruitTranslation TRUTH_IT = new DevilFruitTranslation("Frutto Homo Homo, modello Nika",
			"Il frutto del dio del sole", "Libera chi lo mangia", "Stanca");

	public static final DevilFruitTranslation TRUTH_EN = new DevilFruitTranslation("Human-Human Fruit, Model: Nika",
			"The fruit of the sun god", "Frees its eater", "Tiring");

	/**
	 * At the first appearance: the Gomu Gomu no Mi, a Paramecia; no disadvantages known.
	 */
	public static final DevilFruitBelief INITIAL_KNOWLEDGE = new DevilFruitBelief("Gomu Gomu no Mi", PARAMECIA, null,
			Map.of("it", new DevilFruitTranslation("Frutto Gom Gom", "Gomma, niente di più", null, null), "en",
					new DevilFruitTranslation("Gum-Gum Fruit", "Rubber, nothing more", null, null)));

	/** At chapter 100: a body of rubber, the description changing again later. */
	public static final DevilFruitReveal RUBBER_BODY = new DevilFruitReveal(new StoryPoint(CHAPTER_100),
			Set.of(DevilFruitField.DESCRIPTION),
			new DevilFruitBelief(null, null, null,
					Map.of("it", new DevilFruitTranslation(null, "Un corpo di gomma", null, null), "en",
							new DevilFruitTranslation(null, "A body of rubber", null, null))),
			Map.of("it", "Un corpo di gomma", "en", "A body of rubber"));

	/** At chapter 1044: the truth of every field changing, no value repeated. */
	public static final DevilFruitReveal NIKA = new DevilFruitReveal(new StoryPoint(CHAPTER_1044),
			Set.of(DevilFruitField.NAME, DevilFruitField.ROMAJI, DevilFruitField.TYPE, DevilFruitField.DESCRIPTION,
					DevilFruitField.DISADVANTAGES),
			DevilFruitBelief.NONE, Map.of("it", "Il frutto è Nika", "en", "The fruit is Nika"));

	private GomuGomuReveals() {
	}

	/** The fruit as it is stored: the truth, the initial knowledge and both reveals. */
	public static DevilFruit fruit() {
		return new DevilFruit("Hito Hito no Mi, Model: Nika", ZOAN, MYTHICAL, Map.of("it", TRUTH_IT, "en", TRUTH_EN),
				null, new StoryPoint(CHAPTER_1), INITIAL_KNOWLEDGE, List.of(RUBBER_BODY, NIKA));
	}

}
