package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AffiliationVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID CHAPTER_ID = UUID.randomUUID();

	private static final UUID EPISODE_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-21T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwn() {
		var entity = draft();
		var body = new Affiliation("Mugiwara no Ichimi",
				Map.of("it", new AffiliationTranslation("Pirati di Cappello di Paglia", "Ciurma"), "en",
						new AffiliationTranslation("Straw Hat Pirates", "Crew")),
				"abc", new StoryPoint(CHAPTER_ID, EPISODE_ID), AffiliationKind.PIRATE_CREW);

		AffiliationVersionMapper.rewrite(entity, body, UPDATED);
		var version = AffiliationVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body()).isEqualTo(body);
		assertThat(entity.naturalKeyText()).isEqualTo("Mugiwara no Ichimi");
	}

	@Test
	void rewritingReplacesWhatTheVersionSaid() {
		var entity = draft();
		AffiliationVersionMapper.rewrite(entity,
				new Affiliation("Kaigun", Map.of("it", new AffiliationTranslation("Marina", "Forze")), "abc",
						StoryPoint.ofChapter(CHAPTER_ID), AffiliationKind.NAVY),
				CREATED);

		AffiliationVersionMapper.rewrite(entity,
				new Affiliation(null, Map.of("en", new AffiliationTranslation("Navy", null)), null, null, null),
				UPDATED);

		assertThat(entity.getRomaji()).isNull();
		assertThat(entity.getImageId()).isNull();
		assertThat(entity.getKind()).isNull();
		assertThat(entity.getFirstAppearanceChapterId()).isNull();
		assertThat(entity.getTranslations()).containsOnlyKeys("en");
	}

	private static AffiliationVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new AffiliationVersionEntity(workflow);
	}

}
