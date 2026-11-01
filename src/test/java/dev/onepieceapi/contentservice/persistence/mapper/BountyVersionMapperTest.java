package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BountyVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID PREVIOUS_ID = UUID.randomUUID();

	private static final UUID HOLDER_ID = UUID.fromString("7b3e6d1c-2f48-4a90-b5c7-9d0e1f2a3b44");

	private static final UUID CHAPTER_ID = UUID.randomUUID();

	private static final UUID EPISODE_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwn() {
		var entity = draft();
		var firstAppearance = new StoryPoint(CHAPTER_ID, EPISODE_ID);
		var body = Bounty.builder()
			.posterName("Monkey D. Luffy")
			.amount(3_000_000_000L)
			.condition(BountyCondition.DEAD_OR_ALIVE)
			.inactive(true)
			.previousContentId(PREVIOUS_ID)
			.holderContentId(HOLDER_ID)
			.imageId("abc")
			.firstAppearance(firstAppearance)
			.description(List.of(
					new Paragraph(Map.of("it", "Taglia", "en", "Bounty"),
							new StoryWindow(firstAppearance, StoryPoint.ofEpisode(EPISODE_ID))),
					new Paragraph(Map.of("it", "Dopo"), StoryWindow.from(StoryPoint.ofChapter(CHAPTER_ID)))))
			.build();

		BountyVersionMapper.rewrite(entity, body, UPDATED);
		var version = BountyVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body()).isEqualTo(body);
	}

	@Test
	void theKeyOfTheRowIsBuiltFromTheTwoFieldsNotReadFromTheColumn() {
		var entity = draft();

		BountyVersionMapper.rewrite(entity,
				Bounty.builder().posterName("Zoro").amount(320_000_000L).condition(BountyCondition.ONLY_ALIVE).build(),
				UPDATED);

		assertThat(entity.getNaturalKey()).isNull();
		assertThat(entity.naturalKeyText()).isEqualTo("Zoro 320000000");
		assertThat(entity.naturalKey().fields()).containsExactly("posterName", "amount");
	}

	@Test
	void rewritingReplacesWhatTheVersionSaid() {
		var entity = draft();
		BountyVersionMapper.rewrite(entity,
				Bounty.builder()
					.posterName("Luffy")
					.amount(1L)
					.condition(BountyCondition.DEAD_OR_ALIVE)
					.inactive(true)
					.previousContentId(PREVIOUS_ID)
					.imageId("abc")
					.firstAppearance(StoryPoint.ofChapter(CHAPTER_ID))
					.description(List.of(new Paragraph(Map.of("it", "Uno"), StoryWindow.NONE),
							new Paragraph(Map.of("it", "Due"), StoryWindow.NONE)))
					.build(),
				CREATED);

		var rewritten = Bounty.builder()
			.posterName("Luffy")
			.amount(2L)
			.description(List.of(new Paragraph(Map.of("en", "Two"), StoryWindow.NONE)))
			.build();
		BountyVersionMapper.rewrite(entity, rewritten, UPDATED);

		assertThat(entity.getAmount()).isEqualTo(2L);
		assertThat(entity.getCondition()).isNull();
		assertThat(entity.isInactive()).isFalse();
		assertThat(entity.getPreviousContentId()).isNull();
		assertThat(entity.getImageId()).isNull();
		assertThat(entity.getFirstAppearanceChapterId()).isNull();
		assertThat(entity.getParagraphs()).hasSize(1);
		assertThat(BountyVersionMapper.toDomain(entity).body().description()).isEqualTo(rewritten.description());
	}

	private static BountyVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new BountyVersionEntity(workflow);
	}

}
