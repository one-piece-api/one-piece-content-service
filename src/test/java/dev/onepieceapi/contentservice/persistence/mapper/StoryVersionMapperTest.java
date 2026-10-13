package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.StoryTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StoryVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID SAGA_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aSagaVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwn() {
		var entity = new SagaVersionEntity(workflow(2, VersionStatus.REJECTED));
		entity.rewrite(3, Map.of("it", new StoryTranslationEmbeddable("East Blue", "Il mare")), UPDATED);

		var version = StoryVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(2);
		assertThat(version.basedOn()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.REJECTED);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.body()).isEqualTo(new Saga(3, Map.of("it", new StoryTranslation("East Blue", "Il mare"))));
	}

	@Test
	void anArcVersionAlsoSaysItsSaga() {
		var entity = new ArcVersionEntity(workflow(1, VersionStatus.DRAFT));
		entity.rewrite(1, SAGA_ID, Map.of("en", new StoryTranslationEmbeddable("Romance Dawn", null)), UPDATED);

		var version = StoryVersionMapper.toDomain(entity);

		assertThat(version.body())
			.isEqualTo(new Arc(1, SAGA_ID, Map.of("en", new StoryTranslation("Romance Dawn", null))));
	}

	@Test
	void translationsAreReadInTheOrderOfTheirLanguageCode() {
		var entity = new SagaVersionEntity(workflow(1, VersionStatus.DRAFT));
		entity.rewrite(null,
				Map.of("it", new StoryTranslationEmbeddable("Mare", null), "en",
						new StoryTranslationEmbeddable("Sea", null), "fr", new StoryTranslationEmbeddable(null, null)),
				UPDATED);

		var translations = StoryVersionMapper.toDomain(entity).body().translations();

		assertThat(translations.keySet()).containsExactly("en", "fr", "it");
	}

	@Test
	void rewritingAVersionReplacesWhatItSaysAndNotesWhen() {
		var saga = new SagaVersionEntity(workflow(1, VersionStatus.DRAFT));
		saga.rewrite(1, Map.of("it", new StoryTranslationEmbeddable("East Blue", "Il mare")), CREATED);
		var arc = new ArcVersionEntity(workflow(1, VersionStatus.DRAFT));
		arc.rewrite(1, UUID.randomUUID(), Map.of("it", new StoryTranslationEmbeddable("Old", "Old")), CREATED);
		var newSaga = new Saga(5, Map.of("en", new StoryTranslation("Alabasta", null)));
		var newArc = new Arc(7, SAGA_ID, Map.of("en", new StoryTranslation("Arlong Park", "The park")));

		StoryVersionMapper.rewrite(saga, newSaga, UPDATED);
		StoryVersionMapper.rewrite(arc, newArc, UPDATED);

		assertThat(StoryVersionMapper.toDomain(saga).body()).isEqualTo(newSaga);
		assertThat(StoryVersionMapper.toDomain(arc).body()).isEqualTo(newArc);
		assertThat(StoryVersionMapper.toDomain(arc).updatedAt()).isEqualTo(UPDATED);
	}

	private static ContentVersionEntity workflow(int number, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(CONTENT_ID)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(new UserEmbeddable(CHOPPER.id(), CHOPPER.username(), CHOPPER.email()))
			.status(status)
			.createdAt(CREATED)
			.updatedAt(UPDATED)
			.build();
	}

}
