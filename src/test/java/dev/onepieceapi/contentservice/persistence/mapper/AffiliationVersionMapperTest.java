package dev.onepieceapi.contentservice.persistence.mapper;

import java.util.List;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
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
		var firstAppearance = new StoryPoint(CHAPTER_ID, EPISODE_ID);
		var body = Affiliation.builder()
			.romaji("Mugiwara no Ichimi")
			.translations(Map.of("it", new AffiliationTranslation("Pirati di Cappello di Paglia"), "en",
					new AffiliationTranslation("Straw Hat Pirates")))
			.imageId("abc")
			.firstAppearance(firstAppearance)
			.kind(AffiliationKind.PIRATE_CREW)
			.description(List.of(new Paragraph(Map.of("it", "Ciurma", "en", "Crew"), StoryWindow.from(firstAppearance)),
					new Paragraph(Map.of("it", "Poi"),
							new StoryWindow(StoryPoint.ofEpisode(EPISODE_ID), StoryPoint.ofChapter(CHAPTER_ID)))))
			.build();

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
				Affiliation.builder()
					.romaji("Kaigun")
					.translations(Map.of("it", new AffiliationTranslation("Marina")))
					.imageId("abc")
					.firstAppearance(StoryPoint.ofChapter(CHAPTER_ID))
					.kind(AffiliationKind.NAVY)
					.description(paragraph("it", "Forze"))
					.build(),
				CREATED);

		AffiliationVersionMapper.rewrite(entity,
				Affiliation.builder().translations(Map.of("en", new AffiliationTranslation("Navy"))).build(), UPDATED);

		assertThat(entity.getRomaji()).isNull();
		assertThat(entity.getImageId()).isNull();
		assertThat(entity.getKind()).isNull();
		assertThat(entity.getFirstAppearanceChapterId()).isNull();
		assertThat(entity.getTranslations()).containsOnlyKeys("en");
		assertThat(entity.getParagraphs()).isEmpty();
	}

	private static AffiliationVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new AffiliationVersionEntity(workflow);
	}

}
