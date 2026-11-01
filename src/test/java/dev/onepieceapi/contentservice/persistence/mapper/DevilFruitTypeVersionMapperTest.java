package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class DevilFruitTypeVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private static final User ZORO = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwn() {
		var entity = version(2, VersionStatus.REJECTED);
		entity.getVersion().setRejectionReason("Too short");
		entity.setRomaji("Zoan");
		entity.getTranslations().put("it", new TranslationEmbeddable("Zoo Zoo"));

		var version = DevilFruitTypeVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(2);
		assertThat(version.basedOn()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.REJECTED);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.rejectionReason()).isEqualTo("Too short");
		assertThat(version.createdAt()).isEqualTo(CREATED);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body().romaji()).isEqualTo("Zoan");
		assertThat(version.body().translations())
			.containsExactly(entry("it", new DevilFruitTypeTranslation("Zoo Zoo")));
		assertThat(version.body().paragraphs().values()).allMatch(List::isEmpty);
	}

	@Test
	void aVersionNobodyHoldsHasNoClaimantAndAHeldOneHasItsReviewer() {
		var free = version(2, VersionStatus.IN_REVIEW);
		var held = version(2, VersionStatus.IN_REVIEW);
		held.getVersion().setClaimant(UserMapper.toEmbeddable(ZORO));

		assertThat(DevilFruitTypeVersionMapper.toDomain(free).claimant()).isNull();
		assertThat(DevilFruitTypeVersionMapper.toDomain(held).claimant()).isEqualTo(ZORO);
	}

	@Test
	void translationsAreReadInTheOrderOfTheirLanguageCode() {
		var entity = version(1, VersionStatus.DRAFT);
		entity.getTranslations().put("it", new TranslationEmbeddable("Zoo Zoo"));
		entity.getTranslations().put("en", new TranslationEmbeddable("Zoan"));
		entity.getTranslations().put("fr", new TranslationEmbeddable(null));

		var translations = DevilFruitTypeVersionMapper.toDomain(entity).body().translations();

		assertThat(translations.keySet()).containsExactly("en", "fr", "it");
		assertThat(translations.get("fr")).isEqualTo(new DevilFruitTypeTranslation(null));
	}

	@Test
	void theFirstDraftOfAContentIsVersionOneOfItsAuthorSayingWhatWasWritten() {
		var body = DevilFruitType.builder()
			.romaji("Zoan")
			.translations(Map.of("it", new DevilFruitTypeTranslation("Zoo Zoo")))
			.description(paragraph("it", "Animale"))
			.build();

		var entity = DevilFruitTypeVersionMapper.toFirstDraft(CONTENT_ID, CHOPPER, body, UPDATED);

		var version = DevilFruitTypeVersionMapper.toDomain(entity);
		assertThat(entity.getContentId()).isEqualTo(CONTENT_ID);
		assertThat(version.number()).isEqualTo(1);
		assertThat(version.basedOn()).isNull();
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.claimant()).isNull();
		assertThat(version.createdAt()).isEqualTo(UPDATED);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body()).isEqualTo(body);
	}

	@Test
	void aLaterDraftIsOfItsOwnAuthorSayingWhatItsBaseSays() {
		var base = version(1, VersionStatus.PUBLISHED);
		DevilFruitTypeVersionMapper.rewrite(base,
				DevilFruitType.builder()
					.romaji("Zoan")
					.translations(Map.of("it", new DevilFruitTypeTranslation("Zoo Zoo")))
					.description(List.of(new Paragraph(Map.of("it", "Primo"), StoryWindow.NONE),
							new Paragraph(Map.of("it", "Secondo"), StoryWindow.NONE)))
					.disadvantages(paragraph("it", "Mare"))
					.build(),
				UPDATED);
		var later = UPDATED.plusSeconds(60);

		var entity = DevilFruitTypeVersionMapper.toDraftFrom(CONTENT_ID, 3, DevilFruitTypeVersionMapper.toDomain(base),
				ZORO, later);

		var version = DevilFruitTypeVersionMapper.toDomain(entity);
		assertThat(entity.getContentId()).isEqualTo(CONTENT_ID);
		assertThat(version.number()).isEqualTo(3);
		assertThat(version.basedOn()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(ZORO);
		assertThat(version.createdAt()).isEqualTo(later);
		assertThat(version.updatedAt()).isEqualTo(later);
		assertThat(version.body()).isEqualTo(DevilFruitTypeVersionMapper.toDomain(base).body());
		assertThat(version.body().description()).extracting(Paragraph::text)
			.containsExactly(Map.of("it", "Primo"), Map.of("it", "Secondo"));
		assertThat(entity.getParagraphs()).doesNotContainAnyElementsOf(base.getParagraphs());
	}

	@Test
	void rewritingAVersionReplacesWhatItSaysAndNotesWhen() {
		var entity = version(1, VersionStatus.DRAFT);
		entity.setRomaji("Zoan");
		entity.getTranslations().put("it", new TranslationEmbeddable("Zoo Zoo"));
		var body = DevilFruitType.builder()
			.romaji("Dobutsu-kei")
			.translations(Map.of("en", new DevilFruitTypeTranslation("Zoan")))
			.advantages(paragraph("en", "Strength"))
			.build();
		var later = UPDATED.plusSeconds(60);

		DevilFruitTypeVersionMapper.rewrite(entity, body, later);

		var version = DevilFruitTypeVersionMapper.toDomain(entity);
		assertThat(version.body()).isEqualTo(body);
		assertThat(version.createdAt()).isEqualTo(CREATED);
		assertThat(version.updatedAt()).isEqualTo(later);
	}

	private static DevilFruitTypeVersionEntity version(int number, VersionStatus status) {
		var author = new UserEmbeddable(CHOPPER.id(), CHOPPER.username(), CHOPPER.email());
		var workflow = ContentVersionEntity.builder()
			.contentId(CONTENT_ID)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(author)
			.status(status)
			.createdAt(CREATED)
			.updatedAt(UPDATED)
			.build();
		return new DevilFruitTypeVersionEntity(workflow);
	}

	@Test
	void theFirstAppearancesOfTheTypeAndOfItsSubcategoriesAreWrittenAndReadBack() {
		var ofType = StoryPoint.ofChapter(UUID.randomUUID());
		var ofSubcategory = StoryPoint.ofChapter(UUID.randomUUID());
		var ancient = new DevilFruitTypeSubcategory(UUID.randomUUID(),
				Map.of("it", new DevilFruitTypeSubcategoryTranslation("Antico", null)), ofSubcategory);
		var plain = new DevilFruitTypeSubcategory(UUID.randomUUID(), Map.of());
		var body = DevilFruitType.builder()
			.romaji("Zoan")
			.subcategories(List.of(ancient, plain))
			.firstAppearance(ofType)
			.build();

		var read = DevilFruitTypeVersionMapper
			.toDomain(DevilFruitTypeVersionMapper.toFirstDraft(CONTENT_ID, CHOPPER, body, UPDATED))
			.body();

		assertThat(read.firstAppearance()).isEqualTo(ofType);
		assertThat(read.subcategories()).extracting(DevilFruitTypeSubcategory::firstAppearance)
			.containsExactly(ofSubcategory, StoryPoint.NONE);
	}

}
