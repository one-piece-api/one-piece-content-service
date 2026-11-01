package dev.onepieceapi.contentservice.persistence.mapper;

import java.util.List;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class DevilFruitVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID TYPE_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwnEverySectionInItsOrder() {
		var entity = draft();
		var chapter = StoryPoint.ofChapter(UUID.randomUUID());
		var body = DevilFruit.builder()
			.romaji("Gomu Gomu")
			.typeContentId(TYPE_ID)
			.translations(Map.of("it", new DevilFruitTranslation("Gom Gom")))
			.firstAppearance(chapter)
			.description(List.of(new Paragraph(Map.of("it", "Elastico"), StoryWindow.from(chapter)),
					new Paragraph(Map.of("it", "Nika"), new StoryWindow(chapter, StoryPoint.ofEpisode(TYPE_ID)))))
			.advantages(paragraph("it", "Pro"))
			.disadvantages(List.of(new Paragraph(Map.of("it", "Contro"), StoryWindow.NONE),
					new Paragraph(Map.of("en", "Sea"), StoryWindow.NONE)))
			.build();

		DevilFruitVersionMapper.rewrite(entity, body, UPDATED);
		var version = DevilFruitVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body()).isEqualTo(body);
	}

	@Test
	void rewritingReplacesWhatTheVersionSaidIncludingItsType() {
		var entity = draft();
		DevilFruitVersionMapper.rewrite(entity, DevilFruit.builder()
			.romaji("Gomu")
			.typeContentId(TYPE_ID)
			.translations(Map.of("it", new DevilFruitTranslation("Gom"), "en", new DevilFruitTranslation("Rubber")))
			.advantages(paragraph("it", "Pro"))
			.build(), CREATED);

		DevilFruitVersionMapper.rewrite(entity,
				DevilFruit.builder()
					.romaji("Mera")
					.translations(Map.of("en", new DevilFruitTranslation("Fire")))
					.build(),
				UPDATED);

		assertThat(entity.getRomaji()).isEqualTo("Mera");
		assertThat(entity.getTypeContentId()).isNull();
		assertThat(entity.getTranslations()).containsOnlyKeys("en");
		assertThat(DevilFruitVersionMapper.toDomain(entity).body().translations())
			.containsExactly(entry("en", new DevilFruitTranslation("Fire")));
		assertThat(entity.getParagraphs()).isEmpty();
	}

	@Test
	void theFirstAppearanceIsWrittenAndReadBackAndRewritingWithoutOneClearsIt() {
		var entity = draft();
		var chapter = StoryPoint.ofChapter(UUID.randomUUID());
		DevilFruitVersionMapper.rewrite(entity,
				DevilFruit.builder().romaji("Gomu").typeContentId(TYPE_ID).firstAppearance(chapter).build(), CREATED);

		assertThat(entity.getFirstAppearanceChapterId()).isEqualTo(chapter.chapterContentId());
		assertThat(DevilFruitVersionMapper.toDomain(entity).body().firstAppearance()).isEqualTo(chapter);

		DevilFruitVersionMapper.rewrite(entity, DevilFruit.builder().romaji("Gomu").typeContentId(TYPE_ID).build(),
				UPDATED);

		assertThat(DevilFruitVersionMapper.toDomain(entity).body().firstAppearance()).isEqualTo(StoryPoint.NONE);
	}

	private static DevilFruitVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new DevilFruitVersionEntity(workflow);
	}

}
