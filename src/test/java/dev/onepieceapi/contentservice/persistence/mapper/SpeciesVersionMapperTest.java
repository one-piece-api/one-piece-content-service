package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpeciesVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID CHAPTER_ID = UUID.randomUUID();

	private static final UUID EPISODE_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-21T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwn() {
		var entity = draft();
		var body = new Species("Gyojin", Map.of("it", new SpeciesTranslation("Uomo-pesce", "Razza"), "en",
				new SpeciesTranslation("Fish-Man", "Race")), "abc", new StoryPoint(CHAPTER_ID, EPISODE_ID));

		SpeciesVersionMapper.rewrite(entity, body, UPDATED);
		var version = SpeciesVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body()).isEqualTo(body);
		assertThat(entity.naturalKeyText()).isEqualTo("Gyojin");
	}

	@Test
	void rewritingReplacesWhatTheVersionSaid() {
		var entity = draft();
		SpeciesVersionMapper.rewrite(entity, new Species("Kaigun",
				Map.of("it", new SpeciesTranslation("Marina", "Forze")), "abc", StoryPoint.ofChapter(CHAPTER_ID)),
				CREATED);

		SpeciesVersionMapper.rewrite(entity,
				new Species(null, Map.of("en", new SpeciesTranslation("Human", null)), null, null), UPDATED);

		assertThat(entity.getRomaji()).isNull();
		assertThat(entity.getImageId()).isNull();
		assertThat(entity.getFirstAppearanceChapterId()).isNull();
		assertThat(entity.getTranslations()).containsOnlyKeys("en");
	}

	private static SpeciesVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new SpeciesVersionEntity(workflow);
	}

}
