package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitKnowledge;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.readertime.HiddenEntry;
import dev.onepieceapi.contentservice.domain.readertime.HiddenReason;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.readertime.ReaderPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.atChapter;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.fromChapter;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.previews;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * A Devil Fruit previewed at N (workflow 3.7.1): the Gomu Gomu no Mi, a Paramecia, until
 * chapter 1044 reveals the Hito Hito no Mi, Model: Nika, a mythical Zoan.
 */
class DevilFruitPreviewerTest {

	private static final UUID FRUIT = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final UUID PARAMECIA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	private static final UUID ZOAN = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

	private static final UUID MYTHICAL = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

	private static final DevilFruit NIKA = DevilFruit.builder()
		.romaji("hito-hito-no-mi-model-nika")
		.typeContentId(ZOAN)
		.subcategoryId(MYTHICAL)
		.translations(Map.of("it", new DevilFruitTranslation("Frutto Hito Hito, modello Nika")))
		.firstAppearance(atChapter(1))
		.description(List.of(new Paragraph(Map.of("it", "Rende il corpo di gomma"), fromChapter(1)),
				new Paragraph(Map.of("it", "Il frutto del dio del sole"), fromChapter(1044))))
		.initialKnowledge(new DevilFruitKnowledge("gomu-gomu-no-mi", PARAMECIA, null, Map.of("it", "Frutto Gom Gom")))
		.reveals(List.of(new Reveal<>(atChapter(1044),
				Set.of(DevilFruitField.ROMAJI, DevilFruitField.NAME, DevilFruitField.TYPE), DevilFruitKnowledge.NONE,
				Map.of())))
		.build();

	private ReaderPreviews previews;

	private DevilFruitPreviewer previewer;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.previews = previews();
		ContentDefinition<DevilFruitType, DevilFruitTypeVersionEntity> types = mock(ContentDefinition.class);
		doReturn(Map.of(PARAMECIA, type(atChapter(1), List.of()), ZOAN,
				type(atChapter(1), List.of(new DevilFruitTypeSubcategory(MYTHICAL, Map.of(), atChapter(1044))))))
			.when(this.previews)
			.online(eq(types), any());
		this.previewer = new DevilFruitPreviewer(this.previews, types);
	}

	@Test
	void beforeTheRevealTheReaderKnowsTheGomuGomuNoMiAParamecia() {
		Preview<DevilFruit> preview = this.previewer.previewOf(FRUIT, NIKA, ReaderPosition.atChapter(100));

		DevilFruit fruit = preview.body();
		assertThat(fruit.romaji()).isEqualTo("gomu-gomu-no-mi");
		assertThat(fruit.names()).containsEntry("it", "Frutto Gom Gom");
		assertThat(fruit.typeContentId()).isEqualTo(PARAMECIA);
		assertThat(fruit.subcategoryId()).isNull();
		assertThat(fruit.description()).extracting(Paragraph::text)
			.containsExactly(Map.of("it", "Rende il corpo di gomma"));
		assertThat(fruit.initialKnowledge()).isEqualTo(DevilFruitKnowledge.NONE);
		assertThat(fruit.reveals()).isEmpty();
		assertThat(preview.hiddenEntries()).isEmpty();
	}

	@Test
	void fromTheRevealTheReaderKnowsTheMythicalZoan() {
		DevilFruit fruit = this.previewer.previewOf(FRUIT, NIKA, ReaderPosition.atChapter(1044)).body();

		assertThat(fruit.romaji()).isEqualTo("hito-hito-no-mi-model-nika");
		assertThat(fruit.typeContentId()).isEqualTo(ZOAN);
		assertThat(fruit.subcategoryId()).isEqualTo(MYTHICAL);
		assertThat(fruit.description()).hasSize(2);
	}

	@Test
	void aSubcategoryTheReaderHasNotMetIsHiddenAndFlagged() {
		DevilFruit zoanFromTheStart = NIKA.toBuilder()
			.initialKnowledge(DevilFruitKnowledge.NONE)
			.reveals(List.of())
			.build();

		Preview<DevilFruit> preview = this.previewer.previewOf(FRUIT, zoanFromTheStart, ReaderPosition.atChapter(100));

		assertThat(preview.body().typeContentId()).isEqualTo(ZOAN);
		assertThat(preview.body().subcategoryId()).isNull();
		assertThat(preview.hiddenEntries())
			.containsExactly(new HiddenEntry("subcategory", HiddenReason.TARGET_NOT_VISIBLE, 1044));
	}

	@Test
	void aFruitFirstAppearingOnlyInTheMangaIsNotVisibleOnTheAnimeAxis() {
		Preview<DevilFruit> preview = this.previewer.previewOf(FRUIT, NIKA, ReaderPosition.atEpisode(1000));

		assertThat(preview.visible()).isFalse();
		assertThat(preview.body()).isNull();
	}

	private static DevilFruitType type(StoryPoint firstAppearance, List<DevilFruitTypeSubcategory> subcategories) {
		return DevilFruitType.builder().firstAppearance(firstAppearance).subcategories(subcategories).build();
	}

}
