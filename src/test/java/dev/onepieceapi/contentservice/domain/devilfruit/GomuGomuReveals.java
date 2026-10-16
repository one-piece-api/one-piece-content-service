package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The reference case of the reveals (implementation plan of the timeline, T9; ADR-0010),
 * shared by the resolution tests here and by the public API's in TL6: the Hito Hito no
 * Mi, Model: Nika, met at chapter 1 as the Gomu Gomu no Mi, a Paramecia.
 * <ul>
 * <li>until chapter 100 the reader believed a first description ("rubber, nothing
 * more");</li>
 * <li>until chapter 1044 the Gomu Gomu no Mi, a Paramecia, described as rubber - the
 * description changing twice;</li>
 * <li>from chapter 1044 on, the truth: a Mythical Zoan.</li>
 * </ul>
 * Chapters are named by fixed ids; {@link #NUMBERS} numbers them.
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

	/** Until chapter 100: only a first description. */
	public static final DevilFruitReveal FIRST_BELIEF = new DevilFruitReveal(new StoryPoint(CHAPTER_100), null, null,
			null,
			Map.of("it", new DevilFruitTranslation(null, "Gomma, niente di più", null, null), "en",
					new DevilFruitTranslation(null, "Rubber, nothing more", null, null)),
			Map.of("it", "Il frutto ha un nome", "en", "The fruit gets a name"));

	/** Until chapter 1044: the Gomu Gomu no Mi, a Paramecia, of no subcategory. */
	public static final DevilFruitReveal GOMU_GOMU = new DevilFruitReveal(new StoryPoint(CHAPTER_1044),
			"Gomu Gomu no Mi", PARAMECIA, null,
			Map.of("it", new DevilFruitTranslation("Frutto Gom Gom", "Un corpo di gomma", null, null), "en",
					new DevilFruitTranslation("Gum-Gum Fruit", "A body of rubber", null, null)),
			Map.of("it", "Il frutto è Nika", "en", "The fruit is Nika"));

	private GomuGomuReveals() {
	}

	/** The fruit as it is stored: the truth, and both reveals. */
	public static DevilFruit fruit() {
		return new DevilFruit("Hito Hito no Mi, Model: Nika", ZOAN, MYTHICAL, Map.of("it", TRUTH_IT, "en", TRUTH_EN),
				null, new StoryPoint(CHAPTER_1), List.of(FIRST_BELIEF, GOMU_GOMU));
	}

}
