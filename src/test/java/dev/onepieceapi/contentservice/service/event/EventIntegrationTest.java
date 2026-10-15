package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.config.TimelineProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.event.EventTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import dev.onepieceapi.contentservice.domain.event.WorldDate;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.EventVersionMapper;
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
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitDefinition;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitService;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.story.ChapterRules;
import dev.onepieceapi.contentservice.service.story.ChapterService;
import dev.onepieceapi.contentservice.service.validation.ChapterValidator;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import dev.onepieceapi.contentservice.service.validation.EventValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
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

/**
 * Events (implementation plan of the timeline, T2-T5, TL2; ADR-0009) against a real
 * PostgreSQL (Testcontainers): every shape of a time saved or refused, the order of the
 * world axis across world dates and chapters, the workflow as for every entity, the
 * reveal rule from both sides, and the fruits and chapters events keep from being
 * retired. Whoever tries anyway is refused with the reason, even an administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class EventIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private static final String BEFORE_OCCURS_AT = "must not come before occursAt";

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private EventVersionRepository eventRepository;

	@Autowired
	private ChapterVersionRepository chapterRepository;

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

	private EventService events;

	private DevilFruitService fruits;

	private ChapterService chapters;

	/** A type online every fruit seeded is of. */
	private UUID paramecia;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var properties = new RulesProperties(5);
		var chapterNumbers = new ChapterNumbers(this.chapterRepository);
		var reveals = EventRulesFixtures.reveals(this.fruitRepository, this.eventRepository, this.chapterRepository);
		this.events = new EventService(this.eventRepository, this.contentVersionRepository, this.contentRepository,
				new EventValidator(this.eventRepository, this.languageRepository, this.chapterRepository,
						this.fruitRepository, chapterNumbers, new TimelineProperties(1522)),
				new EventRules(this.contentRepository, this.chapterRepository, this.fruitRepository, reveals),
				auditLogService, clock);
		this.fruits = new DevilFruitService(
				new DevilFruitDefinition(this.fruitRepository,
						new DevilFruitValidator(this.fruitRepository, this.languageRepository, this.typeRepository,
								this.chapterRepository, new ChapterNumbers(this.chapterRepository)),
						EventRulesFixtures.devilFruitRules(this.contentRepository, this.typeRepository,
								this.chapterRepository, this.fruitRepository, this.eventRepository)),
				this.contentVersionRepository, this.contentRepository, auditLogService, clock,
				mock(ContentImages.class));
		this.chapters = new ChapterService(this.chapterRepository, this.contentVersionRepository,
				this.contentRepository,
				new ChapterValidator(this.chapterRepository, this.languageRepository, this.arcRepository,
						this.volumeRepository),
				new ChapterRules(this.contentRepository, this.arcRepository, this.volumeRepository,
						this.fruitRepository, this.typeRepository, this.eventRepository, properties),
				auditLogService, clock);
		this.paramecia = typeContent();
	}

	@Test
	void everyShapeOfATimeIsSavedAsWrittenAndReadBackAlike() {
		UUID first = chapter(READY_TO_PUBLISH, 1);
		UUID later = chapter(PUBLISHED, 956);
		var inTheStory = event(new ChapterTime(first)).toBuilder()
			.endsAt(new ChapterTime(later))
			.revealedAt(new StoryPoint(later))
			.build();
		var beforeTheStory = event(world(700, 800, true)).toBuilder().endsAt(world(1498, null, false)).build();
		var startingBefore = event(world(1498, null, false)).toBuilder().endsAt(new ChapterTime(later)).build();
		var draftWithoutTime = Event.builder().translations(titles("Reverie")).build();

		for (Event written : List.of(inTheStory, beforeTheStory, startingBefore, draftWithoutTime)) {
			UUID id = this.events.create(EDITOR, this.chopper, written).id();
			stored();
			assertThat(this.events.getVersion(EDITOR, this.chopper, id, 1).body()).isEqualTo(written);
		}
	}

	@Test
	void anEventMayNotEndBeforeItOccursSoAChapterStartCannotEndAtAWorldDate() {
		UUID first = chapter(READY_TO_PUBLISH, 1);
		UUID later = chapter(READY_TO_PUBLISH, 956);

		var chaptersReversed = refused(
				event(new ChapterTime(later)).toBuilder().endsAt(new ChapterTime(first)).build());
		var chapterThenYear = refused(
				event(new ChapterTime(first)).toBuilder().endsAt(world(1500, null, false)).build());
		var yearsReversed = refused(
				event(world(1500, null, false)).toBuilder().endsAt(world(1400, 1450, true)).build());

		for (var refusal : List.of(chaptersReversed, chapterThenYear, yearsReversed)) {
			assertThat(violationsOf(refusal)).containsExactly(new FieldViolation("endsAt", BEFORE_OCCURS_AT));
		}
	}

	@Test
	void twoWorldDatesInTheSameYearOrOverlappingAreInOrder() {
		var sameYear = event(world(1500, null, false)).toBuilder().endsAt(world(1500, null, true)).build();
		var overlapping = event(world(1498, 1502, true)).toBuilder().endsAt(world(1500, null, false)).build();

		assertThat(this.events.create(EDITOR, this.chopper, sameYear).id()).isNotNull();
		assertThat(this.events.create(EDITOR, this.chopper, overlapping).id()).isNotNull();
	}

	@Test
	void anEventIsNotRevealedBeforeItOccursButMayBeLater() {
		UUID first = chapter(READY_TO_PUBLISH, 1);
		UUID later = chapter(READY_TO_PUBLISH, 956);

		var early = refused(event(new ChapterTime(later)).toBuilder().revealedAt(new StoryPoint(first)).build());
		var late = event(new ChapterTime(first)).toBuilder().revealedAt(new StoryPoint(later)).build();
		var ofTheOldDays = event(world(1500, null, false)).toBuilder().revealedAt(new StoryPoint(first)).build();

		assertThat(violationsOf(early)).containsExactly(new FieldViolation("revealedAt.chapter", BEFORE_OCCURS_AT));
		assertThat(this.events.create(EDITOR, this.chopper, late).id()).isNotNull();
		assertThat(this.events.create(EDITOR, this.chopper, ofTheOldDays).id()).isNotNull();
	}

	@Test
	void aWorldDateEndsInTheYearTheStoryStartsAtTheLatest() {
		var oneYearLate = refused(event(world(1523, null, false)));
		var spanEndingLate = refused(event(world(1520, 1530, true)));

		assertThat(fieldsOf(oneYearLate)).containsExactly("occursAt.worldDate.yearFrom");
		assertThat(fieldsOf(spanEndingLate)).containsExactly("occursAt.worldDate.yearTo");
		assertThat(this.events.create(EDITOR, this.chopper, event(world(1522, null, false))).id()).isNotNull();
	}

	@Test
	void everyChapterAndFruitNamedIsOneThatPassedReview() {
		UUID draftChapter = chapter(DRAFT, 3);
		UUID draftFruit = fruitContent(DRAFT, "Mera Mera", null);

		var byChapter = refused(event(new ChapterTime(draftChapter)));
		var byFruit = refused(event(world(1500, null, false)).toBuilder()
			.devilFruitContentIds(List.of(fruitContent(READY_TO_PUBLISH, "Gomu Gomu", null), draftFruit))
			.build());

		assertThat(fieldsOf(byChapter)).containsExactly("occursAt.chapter");
		assertThat(fieldsOf(byFruit)).containsExactly("participants.devilFruits[" + draftFruit + "]");
	}

	@Test
	void aDraftIsSavedWithoutATimeButNotSubmitted() {
		UUID id = this.events.create(EDITOR, this.chopper, Event.builder().translations(titles("Reverie")).build())
			.id();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.events.submit(EDITOR, this.chopper, id, 1));

		assertThat(fieldsOf(refused)).containsExactly("occursAt", "translations[it].title");
	}

	@Test
	void twoEventsMayShareATitle() {
		this.events.create(EDITOR, this.chopper, event(world(1498, null, false)));

		assertThat(this.events.create(EDITOR, this.chopper, event(world(1502, null, false))).id()).isNotNull();
	}

	@Test
	void anEventBeforeTheStoryWithNoRevealWrittenDoesNotGoOnlineEvenForAnAdministrator() {
		UUID id = eventContent(READY_TO_PUBLISH, event(world(1500, null, false)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.events.publish(ADMIN, this.vivi, id, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.REVEAL_MISSING);
	}

	@Test
	void anEventGoesOnlineOnlyWhileEachFruitTakingPartIsAndIsToldWhichOne() {
		UUID gomu = fruitContent(PUBLISHED, "Gomu Gomu", chapter(PUBLISHED, 1));
		UUID mera = fruitContent(READY_TO_PUBLISH, "Mera Mera", chapter(PUBLISHED, 1));
		UUID id = eventContent(READY_TO_PUBLISH,
				event(new ChapterTime(chapter(PUBLISHED, 956))).toBuilder()
					.devilFruitContentIds(List.of(gomu, mera))
					.build()
					.normalized());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.events.publish(PUBLISHER, this.vivi, id, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("devilFruitId", mera, "devilFruitRomaji", "Mera Mera"));
	}

	@Test
	void anEventGoesOnlineOnlyWhileTheChaptersItUsesAre() {
		UUID retired = chapter(RETIRED, 956);
		UUID id = eventContent(READY_TO_PUBLISH,
				event(new ChapterTime(chapter(PUBLISHED, 1))).toBuilder().revealedAt(new StoryPoint(retired)).build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.events.publish(PUBLISHER, this.vivi, id, 1));

		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("revealedAtChapterId", retired, "revealedAtChapterNumber", 956));
	}

	@Test
	void anEventIsNotRevealedBeforeAFruitTakingPartFirstAppearsAndListsThemAll() {
		UUID chapterOne = chapter(PUBLISHED, 1);
		UUID chapterHundred = chapter(PUBLISHED, 100);
		UUID gomu = fruitContent(PUBLISHED, "Gomu Gomu", chapterOne);
		UUID mera = fruitContent(PUBLISHED, "Mera Mera", chapter(PUBLISHED, 158));
		UUID early = eventContent(READY_TO_PUBLISH, participating(chapterHundred, gomu, mera));
		UUID fine = eventContent(READY_TO_PUBLISH,
				participating(chapterOne, gomu).toBuilder().revealedAt(new StoryPoint(chapterHundred)).build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.events.publish(ADMIN, this.vivi, early, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.REVEALED_BEFORE_PARTICIPANT);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "fruits", List.of(Map.of("id", mera, "romaji", "Mera Mera"))));
		assertThat(this.events.publish(PUBLISHER, this.vivi, fine, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aFruitDoesNotGoOnlineFirstAppearingAfterAnOnlineEventItTakesPartInIsRevealed() {
		UUID chapterOne = chapter(PUBLISHED, 1);
		UUID chapterHundred = chapter(PUBLISHED, 100);
		UUID gomu = fruitContent(PUBLISHED, "Gomu Gomu", chapterOne);
		UUID event = eventContent(PUBLISHED, participating(chapterOne, gomu));
		fruitVersion(gomu, 2, READY_TO_PUBLISH, "Gomu Gomu", chapterHundred);
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(ADMIN, this.vivi, gomu, 2));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.EVENTS_REVEALED_BEFORE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "events", List.of(Map.of("id", event, "names", Map.of("en", "Reverie")))));
	}

	@Test
	void aFruitTakingPartInOnlineEventsIsNotRetiredAndNamesThemByTitle() {
		UUID chapterOne = chapter(PUBLISHED, 1);
		UUID gomu = fruitContent(PUBLISHED, "Gomu Gomu", chapterOne);
		UUID event = eventContent(PUBLISHED, participating(chapterOne, gomu));
		eventContent(DRAFT, participating(chapterOne, gomu));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.retire(ADMIN, this.vivi, gomu, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "events", List.of(Map.of("id", event, "names", Map.of("en", "Reverie")))));
	}

	@Test
	void aChapterAnOnlineEventEndsOrIsRevealedInIsNotRetired() {
		UUID chapterOne = chapter(PUBLISHED, 1);
		UUID ending = chapter(PUBLISHED, 50);
		UUID revealing = chapter(PUBLISHED, 60);
		eventContent(PUBLISHED,
				event(new ChapterTime(chapterOne)).toBuilder()
					.endsAt(new ChapterTime(ending))
					.revealedAt(new StoryPoint(revealing))
					.build());
		stored();

		for (UUID chapter : List.of(chapterOne, ending, revealing)) {
			var refused = catchThrowableOfType(VersionActionBlockedException.class,
					() -> this.chapters.retire(ADMIN, this.vivi, chapter, 1));
			assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		}
	}

	@Test
	void theListIsNarrowedToTheEventsOfAFruit() {
		UUID chapterOne = chapter(PUBLISHED, 1);
		UUID gomu = fruitContent(PUBLISHED, "Gomu Gomu", chapterOne);
		UUID mera = fruitContent(PUBLISHED, "Mera Mera", chapterOne);
		UUID ofGomu = eventContent(PUBLISHED, participating(chapterOne, gomu, mera).normalized());
		eventContent(PUBLISHED, participating(chapterOne, mera));
		stored();

		var filter = new ContentFilter(null, null, null, null).relatedTo("devilFruit", gomu);
		var page = this.events.list(EDITOR, this.chopper, filter, PageRequest.of(0, 20), "en");

		assertThat(page.getContent()).extracting(ContentSummary::contentId).containsExactly(ofGomu);
	}

	private ValueInvalidException refused(Event written) {
		return catchThrowableOfType(ValueInvalidException.class,
				() -> this.events.create(EDITOR, this.chopper, written));
	}

	/** Titled in English only - the catalog has it and Italian; a draft needs neither. */
	private static Event event(EventTime occursAt) {
		return Event.builder().occursAt(occursAt).translations(titles("Reverie")).build();
	}

	private static Event participating(UUID chapter, UUID... fruits) {
		return event(new ChapterTime(chapter)).toBuilder().devilFruitContentIds(List.of(fruits)).build();
	}

	private static Map<String, StoryTranslation> titles(String title) {
		return Map.of("en", new StoryTranslation(title, null));
	}

	private static EventTime world(int yearFrom, Integer yearTo, boolean approximate) {
		return new WorldDateTime(new WorldDate(yearFrom, yearTo, approximate));
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

	private UUID chapter(VersionStatus status, int number) {
		return OnlineChapters.chapter(this.contentRepository, this.chapterRepository, status, number);
	}

	private UUID content(EntityType entityType) {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	/** An event with a single version in this status. */
	private UUID eventContent(VersionStatus status, Event body) {
		UUID contentId = content(EntityType.EVENT);
		var version = new EventVersionEntity(workflow(contentId, 1, status));
		EventVersionMapper.rewrite(version, body, EARLIER);
		this.eventRepository.save(version);
		return contentId;
	}

	/** A type online, first appearing in a chapter of its own. */
	private UUID typeContent() {
		UUID contentId = content(EntityType.DEVIL_FRUIT_TYPE);
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, 1, PUBLISHED));
		var translations = Map.of("en", new DevilFruitTypeTranslation("Paramecia", "Description", "Pros", "Cons"));
		DevilFruitTypeVersionMapper.rewrite(version,
				new DevilFruitType("Paramecia", translations, List.of(), new StoryPoint(chapter(PUBLISHED, 1))),
				EARLIER);
		this.typeRepository.save(version);
		return contentId;
	}

	/** A fruit of the type online with a single version in this status. */
	private UUID fruitContent(VersionStatus status, String romaji, UUID firstAppearance) {
		UUID contentId = content(EntityType.DEVIL_FRUIT);
		fruitVersion(contentId, 1, status, romaji, firstAppearance);
		return contentId;
	}

	private void fruitVersion(UUID contentId, int number, VersionStatus status, String romaji, UUID firstAppearance) {
		var version = new DevilFruitVersionEntity(workflow(contentId, number, status));
		var translations = Map.of("en", new DevilFruitTranslation("Fruit", "Description", "Pros", "Cons"));
		DevilFruitVersionMapper.rewrite(version,
				new DevilFruit(romaji, this.paramecia, null, translations, null, new StoryPoint(firstAppearance)),
				EARLIER);
		this.fruitRepository.save(version);
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
