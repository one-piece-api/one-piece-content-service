package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitDefinition;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitService;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeRules;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeService;
import dev.onepieceapi.contentservice.service.event.ChapterNumbers;
import dev.onepieceapi.contentservice.service.event.EventRulesFixtures;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.story.ChapterRules;
import dev.onepieceapi.contentservice.service.story.ChapterService;
import dev.onepieceapi.contentservice.service.validation.ChapterValidator;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The reveals of a Devil Fruit (implementation plan of the timeline, T9; ADR-0010)
 * against a real PostgreSQL (Testcontainers): written and read again in order, refused on
 * save when out of the story's order or naming what cannot be linked, kept offline while
 * what they name is, and holding online the chapters and types they name.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class RevealIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private ChapterVersionRepository chapterRepository;

	@Autowired
	private EventVersionRepository eventRepository;

	@Autowired
	private ArcVersionRepository arcRepository;

	@Autowired
	private VolumeVersionRepository volumeRepository;

	@Autowired
	private DevilFruitVersionRepository fruitRepository;

	@Autowired
	private DevilFruitTypeVersionRepository typeRepository;

	@Autowired
	private ContentVersionRepository contentVersionRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TestEntityManager entityManager;

	private DevilFruitService fruits;

	private DevilFruitTypeService types;

	private ChapterService chapters;

	/** A type online, with no subcategory, every fruit seeded is of. */
	private UUID paramecia;

	/** The chapter online, numbered 1, every fruit seeded first appears in. */
	private StoryPoint first;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var properties = new RulesProperties(5);
		this.fruits = new DevilFruitService(
				new DevilFruitDefinition(this.fruitRepository,
						new DevilFruitValidator(this.fruitRepository, this.languageRepository, this.typeRepository,
								this.chapterRepository, new ChapterNumbers(this.chapterRepository)),
						EventRulesFixtures.devilFruitRules(this.contentRepository, this.typeRepository,
								this.chapterRepository, this.fruitRepository, this.eventRepository)),
				this.contentVersionRepository, this.contentRepository, auditLogService, clock,
				mock(ContentImages.class));
		this.types = new DevilFruitTypeService(this.typeRepository, this.contentVersionRepository,
				this.contentRepository,
				new DevilFruitTypeValidator(this.typeRepository, this.languageRepository, this.chapterRepository),
				new DevilFruitTypeRules(this.contentRepository, this.fruitRepository, this.chapterRepository,
						properties),
				auditLogService, clock);
		this.chapters = new ChapterService(this.chapterRepository, this.contentVersionRepository,
				this.contentRepository,
				new ChapterValidator(this.chapterRepository, this.languageRepository, this.arcRepository,
						this.volumeRepository),
				new ChapterRules(this.contentRepository, this.arcRepository, this.volumeRepository,
						this.fruitRepository, this.typeRepository, this.eventRepository, properties),
				auditLogService, clock);
		this.first = new StoryPoint(chapter(PUBLISHED, 1));
		this.paramecia = typeContent(PUBLISHED, type("Paramecia", List.of()));
	}

	@Test
	void revealsAreReadAgainInOrderAndARewriteWithFewerKeepsOnlyThose() {
		UUID logia = typeContent(PUBLISHED, type("Logia", List.of()));
		var believed = new DevilFruitReveal(new StoryPoint(chapter(PUBLISHED, 2)), "Gomu Gomu", logia, null,
				Map.of("it", new DevilFruitTranslation("Gom Gom", null, null, null)));
		var last = reveal(chapter(PUBLISHED, 3), "Gomu Gomu no Mi");

		UUID fruit = this.fruits.create(EDITOR, this.chopper, fruit(believed, last), ImageChange.KEEP).id();
		stored();

		assertThat(this.fruits.getVersion(EDITOR, this.chopper, fruit, 1).version().body().reveals())
			.containsExactly(believed, last);

		this.fruits.edit(EDITOR, this.chopper, fruit, 1, fruit(last));
		stored();

		assertThat(this.fruits.getVersion(EDITOR, this.chopper, fruit, 1).version().body().reveals())
			.containsExactly(last);
	}

	@Test
	void aSaveIsRefusedForAnEmptyRevealAndARevealWithoutChapterBeforeWhatRevealsPointTo() {
		var empty = new DevilFruitReveal(new StoryPoint(chapter(PUBLISHED, 2)), null, null, null, Map.of());
		var noChapter = new DevilFruitReveal(StoryPoint.NONE, "Gomu", null, null, Map.of());
		var draft = reveal(chapter(DRAFT, 4), "Gomu Gomu");

		var incomplete = catchThrowableOfType(ValueInvalidException.class,
				() -> this.fruits.create(EDITOR, this.chopper, fruit(empty, noChapter, draft), ImageChange.KEEP));
		var unlinkable = catchThrowableOfType(ValueInvalidException.class,
				() -> this.fruits.create(EDITOR, this.chopper, fruit(draft), ImageChange.KEEP));

		assertThat(violationsOf(incomplete)).containsExactly(
				new FieldViolation("reveals[0]", "must define at least one field"),
				new FieldViolation("reveals[1].revealedAt.chapter", "is required"));
		assertThat(violationsOf(unlinkable)).containsExactly(
				new FieldViolation("reveals[0].revealedAt.chapter", "must be a Chapter with an approved version"));
	}

	@Test
	void aSaveIsRefusedForRevealsOutOfTheStorysOrder() {
		var early = reveal(this.first.chapterContentId(), "Gomu");
		var late = reveal(chapter(PUBLISHED, 10), "Gomu Gomu");
		var back = reveal(chapter(PUBLISHED, 5), "Gomu Gomu no Mi");

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.fruits.create(EDITOR, this.chopper, fruit(early, late, back), ImageChange.KEEP));

		assertThat(violationsOf(refused)).containsExactly(
				new FieldViolation("reveals[0].revealedAt.chapter", "must come after the first appearance"),
				new FieldViolation("reveals[2].revealedAt.chapter", "must come after the previous reveal"));
	}

	@Test
	void aRevealSubcategoryMustBeOfTheTypeInForceThereTakenFromALaterRevealOrTheTruth() {
		UUID mythical = UUID.randomUUID();
		UUID zoan = typeContent(PUBLISHED, type("Dobutsu", List.of(subcategory(mythical))));
		var onlySubcategory = new DevilFruitReveal(new StoryPoint(chapter(PUBLISHED, 2)), null, null, mythical,
				Map.of());
		var ofItsType = new DevilFruitReveal(new StoryPoint(chapter(PUBLISHED, 3)), null, zoan, mythical, Map.of());

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.fruits.create(EDITOR, this.chopper, fruit(onlySubcategory), ImageChange.KEEP));
		var created = this.fruits.create(EDITOR, this.chopper, fruit(onlySubcategory, ofItsType), ImageChange.KEEP);

		assertThat(violationsOf(refused)).extracting(FieldViolation::field).containsExactly("reveals[0].subcategory");
		assertThat(created.versions().getFirst().body().reveals()).hasSize(2);
	}

	@Test
	void theLastRevealWithoutADescriptionInEveryLanguageIsNotSubmitted() {
		var undescribed = new DevilFruitReveal(new StoryPoint(chapter(PUBLISHED, 2)), "Gomu", null, null,
				Map.of("it", new DevilFruitTranslation(null, "Gomma", null, null)));
		UUID fruit = this.fruits.create(EDITOR, this.chopper, fruit(undescribed), ImageChange.KEEP).id();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.fruits.submit(EDITOR, this.chopper, fruit, 1));

		assertThat(violationsOf(refused)).extracting(FieldViolation::field)
			.containsExactly("reveals[0].translations[en].description");
	}

	@Test
	void aNewVersionDifferingOnlyByARevealIsNotIdentical() {
		var reveal = reveal(chapter(PUBLISHED, 2), "Gomu Gomu");
		UUID fruit = fruitContent(PUBLISHED, fruit(reveal));
		stored();
		this.fruits.openNewVersion(EDITOR, this.chopper, fruit, 1);

		assertThat(catchThrowableOfType(VersionIdenticalException.class,
				() -> this.fruits.submit(EDITOR, this.chopper, fruit, 2)))
			.isNotNull();

		this.fruits.edit(EDITOR, this.chopper, fruit, 2, fruit(reveal(reveal.revealedAtChapterId(), "Gomu Gomu 2")));

		assertThat(this.fruits.submit(EDITOR, this.chopper, fruit, 2).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
	}

	@Test
	void aFruitGoesOnlineOnlyWhileTheRevealChaptersAreAndIsToldWhichReveal() {
		UUID retired = chapter(RETIRED, 7);
		UUID fruit = fruitContent(READY_TO_PUBLISH, fruit(reveal(retired, "Gomu Gomu")));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(ADMIN, this.vivi, fruit, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("reveal", 0, "revealedAtChapterId", retired, "revealedAtChapterNumber", 7));
	}

	@Test
	void aFruitWhoseRevealsAreOutOfOrderOnlineIsNotPublished() {
		UUID fruit = fruitContent(READY_TO_PUBLISH,
				fruit(reveal(chapter(PUBLISHED, 10), "Gomu"), reveal(chapter(PUBLISHED, 5), "Gomu Gomu")));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(ADMIN, this.vivi, fruit, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.REVEALS_OUT_OF_ORDER);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("reveal", 1));
	}

	@Test
	void aFruitGoesOnlineOnlyWhileARevealSubcategoryIsOnline() {
		UUID mythical = UUID.randomUUID();
		UUID zoan = typeContent(PUBLISHED, type("Dobutsu", List.of()));
		UUID fruit = fruitContent(READY_TO_PUBLISH,
				fruit(new DevilFruitReveal(new StoryPoint(chapter(PUBLISHED, 2)), null, zoan, mythical, Map.of())));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(ADMIN, this.vivi, fruit, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.SUBCATEGORY_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("reveal", 0, "typeId", zoan, "subcategoryId", mythical));
	}

	@Test
	void aChapterOrATypeAnOnlineFruitBelievesInIsNotRetired() {
		UUID chapter = chapter(PUBLISHED, 2);
		UUID logia = typeContent(PUBLISHED, type("Logia", List.of()));
		UUID fruit = fruitContent(PUBLISHED,
				fruit(new DevilFruitReveal(new StoryPoint(chapter), "Gomu", logia, null,
						Map.of("it", new DevilFruitTranslation(null, "Gomma", null, null), "en",
								new DevilFruitTranslation(null, "Rubber", null, null)))));
		stored();

		var byChapter = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.chapters.retire(ADMIN, this.vivi, chapter, 1));
		var byType = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.retire(ADMIN, this.vivi, logia, 1));

		var named = Map.of("count", 1L, "fruits", List.of(Map.of("id", fruit, "romaji", "Hito Hito")));
		assertThat(byChapter.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		assertThat(byChapter.getDetails().get("detail")).isEqualTo(named);
		assertThat(byType.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
	}

	@Test
	void aTypeVersionDroppingASubcategoryAnOnlineFruitBelievesInIsNotPublished() {
		UUID mythical = UUID.randomUUID();
		UUID zoan = typeContent(PUBLISHED, type("Dobutsu", List.of(subcategory(mythical))));
		typeVersion(zoan, 2, READY_TO_PUBLISH, type("Dobutsu", List.of()));
		fruitContent(PUBLISHED,
				fruit(new DevilFruitReveal(new StoryPoint(chapter(PUBLISHED, 2)), null, zoan, mythical, Map.of())));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.publish(ADMIN, this.vivi, zoan, 2));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.SUBCATEGORY_IN_USE);
	}

	/** Complete in both languages, first appearing in the chapter numbered 1. */
	private DevilFruit fruit(DevilFruitReveal... reveals) {
		return new DevilFruit("Hito Hito", this.paramecia, null, fruitTranslations(), null, this.first,
				List.of(reveals));
	}

	/** Revealed at this chapter, describing the fruit in both languages. */
	private static DevilFruitReveal reveal(UUID chapter, String romaji) {
		return new DevilFruitReveal(new StoryPoint(chapter), romaji, null, null,
				Map.of("it", new DevilFruitTranslation(null, "Gomma", null, null), "en",
						new DevilFruitTranslation(null, "Rubber", null, null)));
	}

	private static Map<String, DevilFruitTranslation> fruitTranslations() {
		return Map.of("it", new DevilFruitTranslation("Frutto", "Descrizione", "Pro", "Contro"), "en",
				new DevilFruitTranslation("Fruit", "Description", "Pros", "Cons"));
	}

	/** Complete in both languages, first appearing in the chapter numbered 1. */
	private DevilFruitType type(String romaji, List<DevilFruitTypeSubcategory> subcategories) {
		return new DevilFruitType(romaji,
				Map.of("it", new DevilFruitTypeTranslation(romaji + " it", "Descrizione", "Pro", "Contro"), "en",
						new DevilFruitTypeTranslation(romaji, "Description", "Pros", "Cons")),
				subcategories, this.first);
	}

	private DevilFruitTypeSubcategory subcategory(UUID id) {
		return new DevilFruitTypeSubcategory(id,
				Map.of("it", new DevilFruitTypeSubcategoryTranslation("Mitologico", "Descrizione"), "en",
						new DevilFruitTypeSubcategoryTranslation("Mythical", "Description")),
				this.first);
	}

	private UUID chapter(VersionStatus status, int number) {
		return OnlineChapters.chapter(this.contentRepository, this.chapterRepository, status, number);
	}

	private static List<FieldViolation> violationsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations;
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID content(EntityType entityType) {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	/** A type with a first version in this status. */
	private UUID typeContent(VersionStatus status, DevilFruitType body) {
		UUID contentId = content(EntityType.DEVIL_FRUIT_TYPE);
		typeVersion(contentId, 1, status, body);
		return contentId;
	}

	private void typeVersion(UUID contentId, int number, VersionStatus status, DevilFruitType body) {
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, number, status));
		DevilFruitTypeVersionMapper.rewrite(version, body, EARLIER);
		this.typeRepository.save(version);
	}

	/** A fruit with a single version in this status. */
	private UUID fruitContent(VersionStatus status, DevilFruit body) {
		UUID contentId = content(EntityType.DEVIL_FRUIT);
		var version = new DevilFruitVersionEntity(workflow(contentId, 1, status));
		DevilFruitVersionMapper.rewrite(version, body, EARLIER);
		this.fruitRepository.save(version);
		return contentId;
	}

	private ContentVersionEntity workflow(UUID contentId, int number, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(this.chopper))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
	}

}
