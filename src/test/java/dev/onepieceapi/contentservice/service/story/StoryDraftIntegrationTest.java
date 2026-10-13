package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.validation.ArcValidator;
import dev.onepieceapi.contentservice.service.validation.SagaValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Creating a saga or an arc and editing its draft against a real PostgreSQL
 * (Testcontainers) - the same workflow as every content, covered with the Devil Fruit
 * Type - and what is their own: the number as the natural key (unique among the contents
 * of the entity, across every version, gaps allowed), the title as the name, and the link
 * of an arc to a saga that passed review (implementation plan of the story structure, S2,
 * S3, S5).
 * <p>
 * Seeded for every test, all by chopper:
 * <ul>
 * <li>sagas: East Blue (number 1) and Alabasta (number 7, v1 superseded by a v2 online),
 * each online and complete in both languages; Grand Line (number 9), a draft and nothing
 * else</li>
 * <li>arc Romance Dawn (number 1) - v1 online, of East Blue</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class StoryDraftIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private SagaVersionRepository sagaRepository;

	@Autowired
	private ArcVersionRepository arcRepository;

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

	private SagaService sagas;

	private ArcService arcs;

	private UUID eastBlue;

	private UUID alabasta;

	private UUID grandLine;

	private UUID romanceDawn;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		this.sagas = new SagaService(this.sagaRepository, this.contentVersionRepository, this.contentRepository,
				new SagaValidator(this.sagaRepository, this.languageRepository),
				new SagaRules(this.contentRepository, this.arcRepository, new RulesProperties(5)), auditLogService,
				clock);
		this.arcs = new ArcService(this.arcRepository, this.contentVersionRepository, this.contentRepository,
				new ArcValidator(this.arcRepository, this.languageRepository, this.sagaRepository),
				new ArcRules(this.contentRepository, this.sagaRepository), auditLogService, clock);

		this.eastBlue = sagaContent(1, "East Blue", PUBLISHED);
		this.alabasta = sagaContent(7, "Alabasta v1", SUPERSEDED);
		sagaVersion(this.alabasta, 2, PUBLISHED, complete(8, "Alabasta"));
		this.grandLine = sagaContent(9, "Grand Line", DRAFT);
		this.romanceDawn = content(EntityType.ARC);
		arcVersion(this.romanceDawn, 1, PUBLISHED, complete(1, this.eastBlue, "Romance Dawn"));
		stored();
	}

	@Test
	void aNewSagaIsBornWithADraftSayingWhatWasWrittenAndANumberWithGapsIsFine() {
		var written = new Saga(5, Map.of("it", new StoryTranslation(" Mare Blu ", "  ")));

		var content = this.sagas.create(EDITOR, this.nami, written);
		stored();

		var version = this.sagas.getVersion(EDITOR, this.nami, content.id(), 1).version();
		assertThat(version.status()).isEqualTo(DRAFT);
		assertThat(version.author()).isEqualTo(this.nami);
		assertThat(version.body()).isEqualTo(new Saga(5, Map.of("it", new StoryTranslation("Mare Blu", null))));
	}

	@Test
	void theNumberZeroIsAValidNumber() {
		var content = this.sagas.create(EDITOR, this.nami, new Saga(0, Map.of()));
		stored();

		assertThat(this.sagas.getVersion(EDITOR, this.nami, content.id(), 1).version().body().number()).isZero();
	}

	@Test
	void aNumberAnotherSagaHasInAnyOfItsVersionsIsRefused() {
		// 1 is East Blue's online version; 7 is an old version of Alabasta; 9 is a draft.
		for (int taken : List.of(1, 7, 8, 9)) {
			var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
					() -> this.sagas.create(EDITOR, this.nami, new Saga(taken, Map.of())));

			assertThat(fieldsOf(refused)).as("number %s", taken).containsExactly("number");
		}
	}

	@Test
	void aSagaKeepsItsOwnNumberInANewVersion() {
		this.sagas.openNewVersion(EDITOR, this.nami, this.eastBlue, 1);
		stored();

		var edited = this.sagas.edit(EDITOR, this.nami, this.eastBlue, 2, complete(1, "East Blue Revised"));

		assertThat(edited.version().body().number()).isEqualTo(1);
	}

	@Test
	void aTitleAnotherSagaHasInALanguageIsRefused() {
		var written = new Saga(2, Map.of("en", new StoryTranslation(" EAST BLUE ", null)));

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.sagas.create(EDITOR, this.nami, written));

		assertThat(fieldsOf(refused)).containsExactly("translations[en].title");
	}

	@Test
	void theSameTitleInAnotherLanguageIsFree() {
		// East Blue is the English title of another saga; in Italian it is free.
		var written = new Saga(2, Map.of("it", new StoryTranslation("East Blue", null)));

		var content = this.sagas.create(EDITOR, this.nami, written);

		assertThat(content.versions()).hasSize(1);
	}

	@Test
	void aNumberAndATitleAreReportedTogether() {
		var written = new Saga(1, Map.of("en", new StoryTranslation("East Blue", null)));

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.sagas.create(EDITOR, this.nami, written));

		assertThat(fieldsOf(refused)).containsExactly("number", "translations[en].title");
	}

	@Test
	void anArcMayHaveTheNumberOfASagaButNotOfAnotherArc() {
		// Each entity has its own values: number 7 is Alabasta's, not an arc's.
		var content = this.arcs.create(EDITOR, this.nami, new Arc(7, this.eastBlue, Map.of()));
		stored();
		assertThat(content.versions()).hasSize(1);

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.arcs.create(EDITOR, this.nami, new Arc(1, this.eastBlue, Map.of())));

		assertThat(fieldsOf(refused)).containsExactly("number");
	}

	@Test
	void aTitleAnotherArcHasInALanguageIsRefusedButASagaTitleIsFree() {
		var sagaTitle = new Arc(2, this.eastBlue, Map.of("en", new StoryTranslation("East Blue", null)));
		this.arcs.create(EDITOR, this.nami, sagaTitle);

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class, () -> this.arcs.create(EDITOR, this.nami,
				new Arc(3, this.eastBlue, Map.of("en", new StoryTranslation("romance dawn", null)))));

		assertThat(fieldsOf(refused)).containsExactly("translations[en].title");
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "PUBLISHED", "ARCHIVED", "RETIRED", "SUPERSEDED" })
	void aSagaWithAnApprovedVersionCanBeLinkedWhateverBecameOfIt(VersionStatus status) {
		UUID saga = sagaContent(20, "Kodai", status);
		stored();

		var content = this.arcs.create(EDITOR, this.nami, new Arc(2, saga, Map.of()));

		assertThat(content.versions()).hasSize(1);
	}

	@ParameterizedTest
	@EnumSource(names = { "DRAFT", "IN_REVIEW", "REJECTED" })
	void aSagaWithNothingApprovedCannotBeLinked(VersionStatus status) {
		UUID saga = sagaContent(20, "Kodai", status);
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.arcs.create(EDITOR, this.nami, new Arc(2, saga, Map.of())));

		assertThat(fieldsOf(refused)).containsExactly("saga");
	}

	@Test
	void anIdThatIsNoSagaIsRefusedLikeASagaNotToBeLinked() {
		// Nothing there, and a content that exists but is an arc: told alike.
		for (UUID notASaga : List.of(UUID.randomUUID(), this.romanceDawn)) {
			var refused = catchThrowableOfType(ValueInvalidException.class,
					() -> this.arcs.create(EDITOR, this.nami, new Arc(2, notASaga, Map.of())));

			assertThat(fieldsOf(refused)).containsExactly("saga");
		}
	}

	@Test
	void theSagaIsCheckedAgainWhenTheDraftIsEdited() {
		var draft = this.arcs.create(EDITOR, this.nami, new Arc(2, this.eastBlue, Map.of()));
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.arcs.edit(EDITOR, this.nami, draft.id(), 1, new Arc(2, this.grandLine, Map.of())));

		assertThat(fieldsOf(refused)).containsExactly("saga");
	}

	@Test
	void anArcDraftMayHaveNoSagaYet() {
		var content = this.arcs.create(EDITOR, this.nami, new Arc(2, null, Map.of()));
		stored();

		assertThat(this.arcs.getVersion(EDITOR, this.nami, content.id(), 1).version().body().sagaContentId()).isNull();
	}

	@Test
	void anIncompleteArcNamesItsSagaAndItsTitlesWhenSubmitted() {
		UUID draft = content(EntityType.ARC);
		arcVersion(draft, 1, DRAFT, new Arc(2, null, Map.of("it", new StoryTranslation(null, "Descrizione"), "en",
				new StoryTranslation("Orange Town", "d"))));
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.arcs.submit(EDITOR, this.chopper, draft, 1));

		assertThat(fieldsOf(refused)).containsExactly("saga", "translations[it].title");
	}

	@Test
	void anIncompleteSagaNamesItsNumberAndItsTitlesWhenSubmitted() {
		UUID draft = content(EntityType.SAGA);
		sagaVersion(draft, 1, DRAFT, new Saga(null, Map.of("en", new StoryTranslation("Skypiea", "d"))));
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.sagas.submit(EDITOR, this.chopper, draft, 1));

		assertThat(fieldsOf(refused)).containsExactly("number", "translations[it].title",
				"translations[it].description");
	}

	@Test
	void aCompleteArcIsSentToReview() {
		UUID draft = content(EntityType.ARC);
		arcVersion(draft, 1, DRAFT, complete(2, this.eastBlue, "Orange Town"));
		stored();

		assertThat(this.arcs.submit(EDITOR, this.chopper, draft, 1).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
	}

	@Test
	void theDatabaseRefusesAnArcPointingToAnArc() {
		UUID draft = content(EntityType.ARC);
		arcVersion(draft, 1, DRAFT, complete(2, this.romanceDawn, "Orange Town"));

		assertThatThrownBy(this::stored).hasMessageContaining("fk_arc_version_saga");
	}

	@Test
	void aLanguageAStoryEntitySaysSomethingInIsInUse() {
		assertThat(this.sagaRepository.existsByLanguage("it")).isTrue();
		assertThat(this.arcRepository.existsByLanguage("it")).isTrue();
		assertThat(this.sagaRepository.existsByLanguage("fr")).isFalse();
	}

	private static List<String> fieldsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

	/** Complete in both languages of the catalog. */
	private static Saga complete(int number, String title) {
		return new Saga(number, Map.of("it", new StoryTranslation(title + " it", "Descrizione"), "en",
				new StoryTranslation(title, "Description")));
	}

	/**
	 * Complete in both languages of the catalog, so only what is under test can be
	 * missing.
	 */
	private static Arc complete(int number, UUID saga, String title) {
		return new Arc(number, saga, Map.of("it", new StoryTranslation(title + " it", "Descrizione"), "en",
				new StoryTranslation(title, "Description")));
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID content(EntityType entityType) {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	/** A saga with a single version in this status. */
	private UUID sagaContent(int number, String title, VersionStatus status) {
		UUID contentId = content(EntityType.SAGA);
		sagaVersion(contentId, 1, status, complete(number, title));
		return contentId;
	}

	private void sagaVersion(UUID contentId, int number, VersionStatus status, Saga body) {
		var version = new SagaVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.sagaRepository.save(version);
	}

	private void arcVersion(UUID contentId, int number, VersionStatus status, Arc body) {
		var version = new ArcVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.arcRepository.save(version);
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
