package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BountyVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID PREVIOUS_ID = UUID.randomUUID();

	private static final UUID CHAPTER_ID = UUID.randomUUID();

	private static final UUID EPISODE_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwn() {
		var entity = draft();
		var body = new Bounty("Monkey D. Luffy", 3_000_000_000L, BountyCondition.DEAD_OR_ALIVE, true, PREVIOUS_ID,
				Map.of("it", new BountyTranslation("Taglia"), "en", new BountyTranslation("Bounty")), "abc",
				new StoryPoint(CHAPTER_ID, EPISODE_ID));

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
				new Bounty("Zoro", 320_000_000L, BountyCondition.ONLY_ALIVE, false, null, Map.of(), null, null),
				UPDATED);

		assertThat(entity.getNaturalKey()).isNull();
		assertThat(entity.naturalKeyText()).isEqualTo("Zoro 320000000");
		assertThat(entity.naturalKey().fields()).containsExactly("posterName", "amount");
	}

	@Test
	void rewritingReplacesWhatTheVersionSaid() {
		var entity = draft();
		BountyVersionMapper.rewrite(entity, new Bounty("Luffy", 1L, BountyCondition.DEAD_OR_ALIVE, true, PREVIOUS_ID,
				Map.of("it", new BountyTranslation("Uno")), "abc", StoryPoint.ofChapter(CHAPTER_ID)), CREATED);

		BountyVersionMapper.rewrite(entity,
				new Bounty("Luffy", 2L, null, false, null, Map.of("en", new BountyTranslation("Two")), null, null),
				UPDATED);

		assertThat(entity.getAmount()).isEqualTo(2L);
		assertThat(entity.getCondition()).isNull();
		assertThat(entity.isInactive()).isFalse();
		assertThat(entity.getPreviousContentId()).isNull();
		assertThat(entity.getImageId()).isNull();
		assertThat(entity.getFirstAppearanceChapterId()).isNull();
		assertThat(entity.getTranslations()).containsOnlyKeys("en");
	}

	private static BountyVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new BountyVersionEntity(workflow);
	}

}
