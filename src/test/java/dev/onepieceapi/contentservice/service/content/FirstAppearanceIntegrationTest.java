package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
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
import dev.onepieceapi.contentservice.service.event.EventRulesFixtures;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
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

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The first appearance of fruits, types and subcategories (implementation plan of the
 * timeline, T7; ADR-0008) against a real PostgreSQL (Testcontainers): a save names only a
 * chapter with an approved version, a fruit or a type goes online - published or restored
 * - only with a first appearance in a chapter online, and so does a type for each
 * subcategory naming one; a chapter is not retired while online fruits or types first
 * appear in it. Whoever tries anyway is refused with the reason, even an administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class FirstAppearanceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private static final String NOT_LINKABLE = "must be a Chapter with an approved version";

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

	/**
	 * A type online every fruit seeded is of, first appearing in a chapter of its own.
	 */
	private UUID paramecia;

	/** How many subcategories were written so far. */
	private int subcategories;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var properties = new RulesProperties(5);
		this.fruits = new DevilFruitService(
				new DevilFruitDefinition(this.fruitRepository,
						new DevilFruitValidator(this.fruitRepository, this.languageRepository, this.typeRepository,
								this.chapterRepository),
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
		this.paramecia = typeContent(PUBLISHED, type("Paramecia", onlineChapter()));
	}

	@Test
	void aSaveNamingAChapterNothingApprovedOfIsRefusedForTheTypeAndEachSubcategory() {
		var draft = new StoryPoint(chapter(DRAFT));
		var unknown = new StoryPoint(UUID.randomUUID());
		var body = new DevilFruitType("Dobutsu", translations("Zoan"),
				List.of(subcategory(null, StoryPoint.NONE), subcategory(null, unknown)), draft);

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.types.create(EDITOR, this.chopper, body));

		assertThat(fieldsOf(refused)).containsExactly("firstAppearance.chapter",
				"subcategories[1].firstAppearance.chapter");
		assertThat(violationsOf(refused)).extracting(FieldViolation::message).containsOnly(NOT_LINKABLE);
	}

	@Test
	void aSaveNamingAChapterNothingApprovedOfIsRefusedForTheFruit() {
		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.fruits.create(EDITOR, this.chopper, fruit("Gomu Gomu", new StoryPoint(chapter(DRAFT)))));

		assertThat(fieldsOf(refused)).containsExactly("firstAppearance.chapter");
	}

	@Test
	void aChapterOnceApprovedIsChosenEvenWhenItIsNoLongerOnline() {
		var retired = new StoryPoint(chapter(RETIRED));

		var created = this.fruits.create(EDITOR, this.chopper, fruit("Gomu Gomu", retired));

		assertThat(created.versions().getFirst().body().firstAppearance()).isEqualTo(retired);
	}

	@Test
	void aFruitWithoutAFirstAppearanceIsSavedButDoesNotGoOnlineEvenForAnAdministrator() {
		UUID fruit = fruitContent(READY_TO_PUBLISH, fruit("Gomu Gomu", StoryPoint.NONE));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(ADMIN, this.vivi, fruit, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.FIRST_APPEARANCE_MISSING);
		assertThat(this.fruits.getVersion(PUBLISHER, this.vivi, fruit, 1).blockedActions())
			.containsExactly(new BlockedAction(VersionAction.PUBLISH, BlockReason.FIRST_APPEARANCE_MISSING, Map.of()));
	}

	@Test
	void whatAFruitLacksItselfIsToldBeforeWhatItPointsTo() {
		UUID offline = typeContent(ARCHIVED, type("Logia", onlineChapter()));
		UUID fruit = fruitContent(READY_TO_PUBLISH,
				new DevilFruit("Mera Mera", offline, null, translations(), null, StoryPoint.NONE));
		stored();

		assertThat(this.fruits.getVersion(PUBLISHER, this.vivi, fruit, 1).blockedActions())
			.extracting(BlockedAction::reason)
			.containsExactly(BlockReason.FIRST_APPEARANCE_MISSING);
	}

	@Test
	void aFruitGoesOnlineOnlyWhileItsChapterIsAndIsToldItsNumber() {
		UUID retired = chapter(RETIRED);
		UUID early = fruitContent(READY_TO_PUBLISH, fruit("Gomu Gomu", new StoryPoint(retired)));
		UUID fine = fruitContent(READY_TO_PUBLISH, fruit("Bara Bara", onlineChapter()));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(ADMIN, this.vivi, early, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("firstAppearanceChapterId", retired, "firstAppearanceChapterNumber", 1));
		assertThat(this.fruits.publish(PUBLISHER, this.vivi, fine, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aTypeWithoutAFirstAppearanceIsNeitherPublishedNorRestored() {
		UUID logia = typeContent(READY_TO_PUBLISH, type("Logia", StoryPoint.NONE));
		UUID zoan = typeContent(SUPERSEDED, type("Zoan", StoryPoint.NONE));
		typeVersion(zoan, 2, PUBLISHED, type("Zoan", onlineChapter()));
		stored();

		var unpublished = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.publish(ADMIN, this.vivi, logia, 1));
		var unrestored = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.restore(ADMIN, this.vivi, zoan, 1));

		assertThat(unpublished.getDetails()).containsEntry("reason", BlockReason.FIRST_APPEARANCE_MISSING);
		assertThat(unrestored.getDetails()).containsEntry("reason", BlockReason.FIRST_APPEARANCE_MISSING);
	}

	@Test
	void aTypeGoesOnlineOnlyWhileItsChapterAndThoseOfItsSubcategoriesAre() {
		UUID retired = chapter(RETIRED);
		UUID subcategory = UUID.randomUUID();
		UUID ownChapterOffline = typeContent(READY_TO_PUBLISH, type("Logia", new StoryPoint(retired)));
		UUID subcategoryChapterOffline = typeContent(READY_TO_PUBLISH,
				new DevilFruitType("Dobutsu", translations("Zoan"),
						List.of(subcategory(UUID.randomUUID(), StoryPoint.NONE),
								subcategory(subcategory, new StoryPoint(retired))),
						onlineChapter()));
		stored();

		var own = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.publish(ADMIN, this.vivi, ownChapterOffline, 1));
		var ofSubcategory = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.publish(ADMIN, this.vivi, subcategoryChapterOffline, 1));

		assertThat(own.getDetails().get("detail"))
			.isEqualTo(Map.of("firstAppearanceChapterId", retired, "firstAppearanceChapterNumber", 1));
		assertThat(ofSubcategory.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(ofSubcategory.getDetails().get("detail")).isEqualTo(Map.of("subcategoryId", subcategory,
				"firstAppearanceChapterId", retired, "firstAppearanceChapterNumber", 1));
	}

	@Test
	void aChapterIsNotRetiredWhileFruitsOnlineFirstAppearInItAndNamesThemFirst() {
		UUID chapter = chapter(PUBLISHED);
		UUID gomu = fruitContent(PUBLISHED, fruit("Gomu Gomu", new StoryPoint(chapter)));
		fruitContent(DRAFT, fruit("Mera Mera", new StoryPoint(chapter)));
		typeContent(PUBLISHED, type("Logia", new StoryPoint(chapter)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.chapters.retire(ADMIN, this.vivi, chapter, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "fruits", List.of(Map.of("id", gomu, "romaji", "Gomu Gomu"))));
	}

	@Test
	void aChapterIsNotRetiredWhileTypesOrTheirSubcategoriesOnlineFirstAppearInIt() {
		UUID ofType = chapter(PUBLISHED);
		UUID ofSubcategory = chapter(PUBLISHED);
		UUID logia = typeContent(PUBLISHED, type("Logia", new StoryPoint(ofType)));
		UUID zoan = typeContent(PUBLISHED, new DevilFruitType("Dobutsu", translations("Zoan"),
				List.of(subcategory(UUID.randomUUID(), new StoryPoint(ofSubcategory))), onlineChapter()));
		stored();

		var byType = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.chapters.retire(RETIRER, this.vivi, ofType, 1));
		var bySubcategory = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.chapters.retire(RETIRER, this.vivi, ofSubcategory, 1));

		assertThat(byType.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "types", List.of(Map.of("id", logia, "romaji", "Logia"))));
		assertThat(bySubcategory.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "types", List.of(Map.of("id", zoan, "romaji", "Dobutsu"))));
	}

	@Test
	void aChapterOnlyItemsNoLongerOnlineFirstAppearInIsRetired() {
		UUID chapter = chapter(PUBLISHED);
		fruitContent(SUPERSEDED, fruit("Gomu Gomu", new StoryPoint(chapter)));
		typeContent(ARCHIVED, type("Logia", new StoryPoint(chapter)));
		stored();

		assertThat(this.chapters.retire(RETIRER, this.vivi, chapter, 1).version().status()).isEqualTo(RETIRED);
	}

	private StoryPoint onlineChapter() {
		return OnlineChapters.onlineChapter(this.contentRepository, this.chapterRepository);
	}

	private UUID chapter(VersionStatus status) {
		return OnlineChapters.chapter(this.contentRepository, this.chapterRepository, status);
	}

	/** Complete in both languages of the catalog. */
	private static DevilFruitType type(String romaji, StoryPoint firstAppearance) {
		return new DevilFruitType(romaji, translations(romaji), List.of(), firstAppearance);
	}

	private static Map<String, DevilFruitTypeTranslation> translations(String name) {
		return Map.of("it", new DevilFruitTypeTranslation(name + " it", "Descrizione", "Pro", "Contro"), "en",
				new DevilFruitTypeTranslation(name, "Description", "Pros", "Cons"));
	}

	/** Complete in both languages, with a name of its own. */
	private DevilFruitTypeSubcategory subcategory(UUID id, StoryPoint firstAppearance) {
		String name = "Subcategory " + ++this.subcategories;
		return new DevilFruitTypeSubcategory(id,
				Map.of("it", new DevilFruitTypeSubcategoryTranslation(name + " it", "Descrizione"), "en",
						new DevilFruitTypeSubcategoryTranslation(name, "Description")),
				firstAppearance);
	}

	/** Complete in both languages of the catalog, of the type online. */
	private DevilFruit fruit(String romaji, StoryPoint firstAppearance) {
		return new DevilFruit(romaji, this.paramecia, null, translations(), null, firstAppearance);
	}

	private static Map<String, DevilFruitTranslation> translations() {
		return Map.of("it", new DevilFruitTranslation("Frutto", "Descrizione", "Pro", "Contro"), "en",
				new DevilFruitTranslation("Fruit", "Description", "Pros", "Cons"));
	}

	private static List<FieldViolation> violationsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations;
	}

	private static List<String> fieldsOf(DomainException refused) {
		return violationsOf(refused).stream().map(FieldViolation::field).toList();
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
