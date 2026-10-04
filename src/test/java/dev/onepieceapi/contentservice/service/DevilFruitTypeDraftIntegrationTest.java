package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.DevilFruitTypeNotFoundException;
import dev.onepieceapi.contentservice.service.exception.TranslationLanguageUnknownException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
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
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.DELETE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.EDIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.OPEN_NEW_VERSION;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.SUBMIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Creating a content, editing its draft and discarding it (UF-CNT-01, UF-CNT-02,
 * UF-CNT-11) against a real PostgreSQL (Testcontainers): what is stored, who may change
 * it, and the uniqueness of romaji and names, which is a query on what every other
 * content says.
 * <p>
 * Seeded for every test, both by chopper:
 * <ul>
 * <li>Zoan - v1 superseded as "Dobutsu", v2 online as "Zoan" (Italian name "Zoo
 * Zoo")</li>
 * <li>Logia - v1 draft (Italian name "Rogia")</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeDraftIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	@Autowired
	private DevilFruitTypeVersionRepository versionRepository;

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

	private DevilFruitTypeService service;

	private UUID zoan;

	private UUID logia;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		this.service = new DevilFruitTypeService(this.versionRepository, this.contentVersionRepository,
				this.contentRepository, validator, auditLogService, clock);

		this.zoan = content();
		version(this.zoan, 1, SUPERSEDED, this.chopper, "Dobutsu", "Zoo Zoo");
		version(this.zoan, 2, PUBLISHED, this.chopper, "Zoan", "Zoo Zoo");
		this.logia = content();
		version(this.logia, 1, DRAFT, this.chopper, "Logia", "Rogia");
		stored();
	}

	@Test
	void aNewContentIsBornWithADraftOfItsAuthorSayingWhatWasWritten() {
		var written = new DevilFruitType("  Chojin-kei ",
				Map.of("it", new DevilFruitTypeTranslation(" Paramisia ", "  ", " Forza ", "	"), "en",
						new DevilFruitTypeTranslation("", null, " ", null)));

		var content = this.service.create(EDITOR, this.nami, written);
		stored();

		var version = this.service.getVersion(EDITOR, this.nami, content.id(), 1).version();
		assertThat(version.status()).isEqualTo(DRAFT);
		assertThat(version.author()).isEqualTo(this.nami);
		assertThat(version.basedOn()).isNull();
		assertThat(version.createdAt()).isEqualTo(NOW);
		assertThat(version.updatedAt()).isEqualTo(NOW);
		assertThat(version.body().romaji()).isEqualTo("Chojin-kei");
		assertThat(version.body().translations())
			.containsExactly(entry("it", new DevilFruitTypeTranslation("Paramisia", null, "Forza", null)));
	}

	@Test
	void theAnswerToACreationIsTheContentWithItsOnlyVersionAndWhatTheAuthorMayDo() {
		var content = this.service.create(EDITOR, this.nami, romajiOnly("Chojin-kei"));

		assertThat(content.onlineVersionNumber()).isEmpty();
		assertThat(content.versions()).hasSize(1);
		VersionAccess<DevilFruitType> first = content.versions().getFirst();
		assertThat(first.version().number()).isEqualTo(1);
		assertThat(first.allowedActions()).containsExactly(EDIT, DELETE, SUBMIT);
	}

	@Test
	void aDraftMayBeBornWithNothingInIt() {
		var content = this.service.create(EDITOR, this.nami, new DevilFruitType(null, Map.of()));
		stored();

		var body = this.service.getVersion(EDITOR, this.nami, content.id(), 1).version().body();
		assertThat(body.romaji()).isNull();
		assertThat(body.translations()).isEmpty();
	}

	@Test
	void aNewDraftIsSeenByEveryEditorButChangedOnlyByItsAuthorAndUnseenByAReviewer() {
		UUID created = this.service.create(EDITOR, this.nami, romajiOnly("Chojin-kei")).id();
		stored();

		assertThat(this.service.getVersion(EDITOR, this.chopper, created, 1).allowedActions()).isEmpty();
		assertThatThrownBy(() -> this.service.get(REVIEWER, this.zoro, created))
			.isInstanceOf(DevilFruitTypeNotFoundException.class);
	}

	@Test
	void editingReplacesEverythingTheDraftSays() {
		var written = new DevilFruitType("Shizen-kei", Map.of("en", translation("Logia", "Elemental")));

		var answer = this.service.edit(EDITOR, this.chopper, this.logia, 1, written);
		stored();

		var version = this.service.getVersion(EDITOR, this.chopper, this.logia, 1).version();
		assertThat(version.body().romaji()).isEqualTo("Shizen-kei");
		// The Italian translation was not sent again: it is gone.
		assertThat(version.body().translations()).containsExactly(entry("en", translation("Logia", "Elemental")));
		assertThat(version.createdAt()).isEqualTo(EARLIER);
		assertThat(version.updatedAt()).isEqualTo(NOW);
		assertThat(answer.version().body()).isEqualTo(version.body());
		assertThat(answer.allowedActions()).containsExactly(EDIT, DELETE, SUBMIT);
	}

	@Test
	void anotherEditorMayNotEditTheDraft() {
		assertThatThrownBy(() -> this.service.edit(EDITOR, this.nami, this.logia, 1, romajiOnly("Mine now")))
			.isInstanceOf(VersionActionForbiddenException.class);
		stored();

		var body = this.service.getVersion(EDITOR, this.nami, this.logia, 1).version().body();
		assertThat(body.romaji()).isEqualTo("Logia");
	}

	@Test
	void aDraftDoesNotExistForWhoCannotSeeIt() {
		assertThatThrownBy(() -> this.service.edit(REVIEWER, this.zoro, this.logia, 1, romajiOnly("Logia")))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@ParameterizedTest
	@EnumSource(
			names = { "IN_REVIEW", "REJECTED", "READY_TO_PUBLISH", "PUBLISHED", "ARCHIVED", "RETIRED", "SUPERSEDED" })
	void onlyADraftIsEdited(VersionStatus status) {
		UUID other = content();
		version(other, 1, status, this.nami, "Kodai", "Antico");
		stored();

		assertThatThrownBy(() -> this.service.edit(EDITOR, this.nami, other, 1, romajiOnly("Kodai Zoan")))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void aRomajiOrANameAnotherContentHasIsRefusedNamingEachField() {
		// Against a draft and against the online version; another case, space around.
		var written = new DevilFruitType(" logia ", Map.of("it", translation("ZOO ZOO", null)));

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.create(EDITOR, this.nami, written));

		assertThat(fieldsOf(refused)).containsExactly("romaji", "translations[it].name");
	}

	@Test
	void aValueOfAVersionNoLongerOnlineIsStillTaken() {
		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.create(EDITOR, this.nami, romajiOnly("Dobutsu")));

		assertThat(fieldsOf(refused)).containsExactly("romaji");
	}

	@Test
	void aNameIsUniqueWithinItsLanguageOnly() {
		var written = new DevilFruitType("Chojin-kei", Map.of("en", translation("Zoo Zoo", null)));

		var content = this.service.create(EDITOR, this.nami, written);

		assertThat(content.versions()).hasSize(1);
	}

	@Test
	void theVersionsOfAContentNeverCollideWithEachOther() {
		version(this.zoan, 3, DRAFT, this.nami, "Dobutsu-kei", "Animale");
		stored();
		// The romaji of v2 and the Italian name of v1 and v2, on v3 of the same content.
		var written = new DevilFruitType("Zoan", Map.of("it", translation("Zoo Zoo", null)));

		var answer = this.service.edit(EDITOR, this.nami, this.zoan, 3, written);

		assertThat(answer.version().body().romaji()).isEqualTo("Zoan");
	}

	@Test
	void aDraftSavedAgainAsItIsDoesNotCollideWithItself() {
		var same = new DevilFruitType("Logia", Map.of("it", translation("Rogia", "Rogia description")));

		var answer = this.service.edit(EDITOR, this.chopper, this.logia, 1, same);

		assertThat(answer.version().body()).isEqualTo(same);
	}

	@Test
	void aLanguageOutsideTheCatalogIsRefused() {
		var written = new DevilFruitType("Chojin-kei", Map.of("fr", translation("Paramecia", null)));

		assertThatThrownBy(() -> this.service.create(EDITOR, this.nami, written))
			.isInstanceOf(TranslationLanguageUnknownException.class);
	}

	@Test
	void aRefusedDraftLeavesNothingBehind() {
		long before = contentsSeenBy(this.nami);

		assertThatThrownBy(() -> this.service.create(EDITOR, this.nami, romajiOnly("Logia")))
			.isInstanceOf(ValueAlreadyUsedException.class);
		stored();

		assertThat(contentsSeenBy(this.nami)).isEqualTo(before);
	}

	@Test
	void everySaveIsRecordedOnTheVersionWithWhoDidIt() {
		UUID created = this.service.create(EDITOR, this.nami, romajiOnly("Chojin-kei")).id();
		this.service.edit(EDITOR, this.nami, created, 1, romajiOnly("Chojin"));
		stored();

		List<VersionEvent> events = this.service.events(EDITOR, created, 1);
		assertThat(events).extracting(VersionEvent::action, VersionEvent::actor)
			.containsExactly(tuple("VERSION_CREATED", this.nami), tuple("VERSION_EDITED", this.nami));
	}

	@Test
	void aRowOfTheListSaysWhatItsCallerMayDoWithTheVersionShown() {
		UUID free = content();
		version(free, 1, PUBLISHED, this.chopper, "Kodai", "Antico");
		stored();

		assertThat(actionsInList(this.chopper)).containsOnly(entry(this.logia, Set.of(EDIT, DELETE, SUBMIT)),
				entry(this.zoan, Set.of(OPEN_NEW_VERSION)), entry(free, Set.of(OPEN_NEW_VERSION)));
		assertThat(actionsInList(this.nami).get(this.logia)).isEmpty();
	}

	@Test
	void aClosedVersionOpensNoNewOneWhileItsContentHasAnOpenVersion() {
		version(this.zoan, 3, DRAFT, this.chopper, "Dobutsu-kei", "Animale");
		stored();

		var chain = this.service.get(EDITOR, this.nami, this.zoan).versions();

		assertThat(chain).extracting(VersionAccess::allowedActions).containsExactly(Set.of(), Set.of(), Set.of());
	}

	@Test
	void discardingAFirstDraftRemovesItsContent() {
		UUID versionId = versionIdOf(this.logia, 1);

		this.service.delete(EDITOR, this.chopper, this.logia, 1);
		stored();

		assertThatThrownBy(() -> this.service.get(EDITOR, this.chopper, this.logia))
			.isInstanceOf(DevilFruitTypeNotFoundException.class);
		assertThat(this.contentVersionRepository.hasOpenVersion(this.logia)).isFalse();
		assertThat(this.entityManager.find(ContentEntity.class, this.logia)).isNull();
		assertThat(this.versionRepository.findById(versionId)).isEmpty();
	}

	@Test
	void aDiscardedDraftSurvivesOnlyInTheAuditLog() {
		UUID versionId = versionIdOf(this.logia, 1);

		this.service.delete(EDITOR, this.chopper, this.logia, 1);
		stored();

		var records = this.auditLogRepository.findByTargetVersionIdOrderByOccurredAtAscIdAsc(versionId);
		assertThat(records).hasSize(1);
		assertThat(records.getFirst().getAction()).isEqualTo("VERSION_DELETED");
		assertThat(records.getFirst().getTargetContentId()).isEqualTo(this.logia);
		assertThat(records.getFirst().getTargetLabel()).isEqualTo("Logia");
		assertThat(records.getFirst().getActor().getUsername()).isEqualTo("chopper");
	}

	@Test
	void discardingALaterDraftTakesTheContentBackToItsPreviousVersion() {
		version(this.zoan, 3, DRAFT, this.chopper, "Dobutsu-kei", "Animale");
		stored();

		this.service.delete(EDITOR, this.chopper, this.zoan, 3);
		stored();

		var chain = this.service.get(EDITOR, this.chopper, this.zoan).versions();
		assertThat(chain).extracting(access -> access.version().number()).containsExactly(1, 2);
		// Nothing is open any more: a new version may be opened from a closed one.
		assertThat(chain.getLast().allowedActions()).containsExactly(OPEN_NEW_VERSION);
	}

	@Test
	void theNumberOfADiscardedDraftIsTakenByTheNextOne() {
		version(this.zoan, 3, DRAFT, this.chopper, "Dobutsu-kei", "Animale");
		stored();
		this.service.delete(EDITOR, this.chopper, this.zoan, 3);
		stored();

		version(this.zoan, 3, DRAFT, this.nami, "Dobutsu-kei", "Animale");
		stored();

		assertThat(this.service.getVersion(EDITOR, this.nami, this.zoan, 3).version().author()).isEqualTo(this.nami);
	}

	@Test
	void anotherEditorMayNotDiscardTheDraft() {
		assertThatThrownBy(() -> this.service.delete(EDITOR, this.nami, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		stored();

		assertThat(this.service.get(EDITOR, this.nami, this.logia).versions()).hasSize(1);
	}

	@Test
	void aDraftWhoCannotSeeItCannotDiscardIt() {
		assertThatThrownBy(() -> this.service.delete(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@ParameterizedTest
	@EnumSource(names = { "REJECTED", "PUBLISHED" })
	void onlyADraftIsDiscarded(VersionStatus status) {
		UUID other = content();
		version(other, 1, status, this.nami, "Kodai", "Antico");
		stored();

		assertThatThrownBy(() -> this.service.delete(EDITOR, this.nami, other, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	private UUID versionIdOf(UUID contentId, int number) {
		return this.versionRepository.findVisible(contentId, number, Set.of(VersionStatus.values()))
			.orElseThrow()
			.getVersionId();
	}

	private Map<UUID, Set<VersionAction>> actionsInList(User caller) {
		Map<UUID, Set<VersionAction>> actions = new HashMap<>();
		for (ContentSummary<DevilFruitType> row : this.service.list(EDITOR, caller, ContentFilter.none(),
				PageRequest.of(0, 20))) {
			actions.put(row.contentId(), row.allowedActions());
		}
		return actions;
	}

	private long contentsSeenBy(User caller) {
		return this.service.summary(EDITOR, caller).total();
	}

	private static List<String> fieldsOf(ValueAlreadyUsedException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

	private static DevilFruitType romajiOnly(String romaji) {
		return new DevilFruitType(romaji, Map.of());
	}

	private static DevilFruitTypeTranslation translation(String name, String description) {
		return new DevilFruitTypeTranslation(name, description, null, null);
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID content() {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT_TYPE, EARLIER))
			.getId();
	}

	/** Seeds one version, based on the previous one, with an Italian translation only. */
	private void version(UUID contentId, int number, VersionStatus status, User author, String romaji,
			String italianName) {
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(author))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new DevilFruitTypeVersionEntity(workflow);
		version.setRomaji(romaji);
		version.getTranslations()
			.put("it", new TranslationEmbeddable(italianName, italianName + " description", "Pro", "Contro"));
		this.versionRepository.save(version);
	}

}
