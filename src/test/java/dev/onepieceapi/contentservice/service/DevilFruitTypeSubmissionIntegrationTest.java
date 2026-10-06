package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
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
import dev.onepieceapi.contentservice.service.exception.SlugAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import dev.onepieceapi.exception.ApplicationException;
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

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.DELETE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.EDIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.PULL_BACK;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.SUBMIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Sending a draft to review and taking it back (UF-CNT-03, UF-CNT-04) against a real
 * PostgreSQL (Testcontainers): who may, from which state, and the three rules a version
 * must meet to go - complete in every language of the catalog (Italian and English, from
 * the baseline), unique, different from the other versions of its content.
 * <p>
 * Seeded for every test, all complete in both languages:
 * <ul>
 * <li>Zoan, by chopper - v1 superseded as "Dobutsu", v2 online as "Zoan"</li>
 * <li>Logia, by nami - v1 draft</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeSubmissionIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

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
		version(this.zoan, 1, SUPERSEDED, this.chopper, complete("Dobutsu"));
		version(this.zoan, 2, PUBLISHED, this.chopper, complete("Zoan"));
		this.logia = content();
		version(this.logia, 1, DRAFT, this.nami, complete("Logia"));
		stored();
	}

	@Test
	void aSubmittedDraftGoesToReviewUnclaimedAndLeavesItsAuthorOnlyThePullBack() {
		var answer = this.service.submit(EDITOR, this.nami, this.logia, 1);
		stored();

		var version = this.service.getVersion(EDITOR, this.nami, this.logia, 1).version();
		assertThat(version.status()).isEqualTo(IN_REVIEW);
		assertThat(version.isClaimed()).isFalse();
		assertThat(version.updatedAt()).isEqualTo(NOW);
		assertThat(answer.version().status()).isEqualTo(IN_REVIEW);
		assertThat(answer.allowedActions()).containsExactly(PULL_BACK);
	}

	@Test
	void reviewersStartSeeingAVersionOnceItIsSubmitted() {
		assertThatThrownBy(() -> this.service.getVersion(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionNotFoundException.class);

		this.service.submit(EDITOR, this.nami, this.logia, 1);
		stored();

		assertThat(this.service.getVersion(REVIEWER, this.zoro, this.logia, 1).version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void anIncompleteDraftIsRefusedNamingEveryMissingFieldInEveryLanguage() {
		UUID empty = content();
		version(empty, 1, DRAFT, this.nami, new DevilFruitType(null, Map.of()));
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, this.nami, empty, 1));

		assertThat(fieldsOf(refused)).containsExactly("romaji", "translations[en].name", "translations[en].description",
				"translations[en].advantages", "translations[en].disadvantages", "translations[it].name",
				"translations[it].description", "translations[it].advantages", "translations[it].disadvantages");
	}

	@Test
	void aLanguageOfTheCatalogLeftOutMakesTheDraftIncomplete() {
		UUID italianOnly = content();
		var body = new DevilFruitType("Kodai", Map.of("it", translation("Antico", "Antico description")));
		version(italianOnly, 1, DRAFT, this.nami, body);
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, this.nami, italianOnly, 1));

		assertThat(fieldsOf(refused)).containsExactly("translations[en].name", "translations[en].description",
				"translations[en].advantages", "translations[en].disadvantages");
		assertStillADraft(italianOnly);
	}

	@Test
	void advantagesAndDisadvantagesAreRequiredForReviewInEveryLanguage() {
		UUID withoutTradeOffs = content();
		var body = new DevilFruitType("Kodai", Map.of("it", translation("Antico", "Antico descrizione"), "en",
				new DevilFruitTypeTranslation("Ancient", "Ancient description", null, null)));
		version(withoutTradeOffs, 1, DRAFT, this.nami, body);
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, this.nami, withoutTradeOffs, 1));

		assertThat(fieldsOf(refused)).containsExactly("translations[en].advantages", "translations[en].disadvantages");
		assertStillADraft(withoutTradeOffs);
	}

	@Test
	void aValueTakenByAnotherContentSinceTheLastSaveIsRefused() {
		// Two saves at the same moment both passed: the second content got "Zoan" too.
		UUID twin = content();
		version(twin, 1, DRAFT, this.nami, complete("Zoan", "Twin"));
		stored();

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.submit(EDITOR, this.nami, twin, 1));

		assertThat(fieldsOf(refused)).containsExactly("romaji");
		assertStillADraft(twin);
	}

	@Test
	void aSlugTakenByAnotherContentSinceTheLastSaveIsRefused() {
		UUID twin = content();
		version(twin, 1, DRAFT, this.nami, complete("Zoān", "Twin"));
		stored();

		var refused = catchThrowableOfType(SlugAlreadyUsedException.class,
				() -> this.service.submit(EDITOR, this.nami, twin, 1));

		assertThat(refused.getDetails()).containsEntry("slug", "zoan");
		assertStillADraft(twin);
	}

	@Test
	void completenessIsCheckedFirst() {
		UUID twin = content();
		version(twin, 1, DRAFT, this.nami, new DevilFruitType("Zoan", Map.of()));
		stored();

		assertThatThrownBy(() -> this.service.submit(EDITOR, this.nami, twin, 1))
			.isInstanceOf(VersionIncompleteException.class);
	}

	@Test
	void aDraftSayingExactlyWhatAnotherVersionSaysIsRefusedNamingIt() {
		version(this.zoan, 3, DRAFT, this.chopper, complete("Dobutsu"));
		stored();

		var refused = catchThrowableOfType(VersionIdenticalException.class,
				() -> this.service.submit(EDITOR, this.chopper, this.zoan, 3));

		assertThat(refused.getDetails()).containsEntry("identicalTo", 1);
		assertStillADraft(this.zoan, 3);
	}

	@Test
	void aChangeOfCaseAloneIsAChange() {
		version(this.zoan, 3, DRAFT, this.chopper, complete("DOBUTSU"));
		stored();

		var answer = this.service.submit(EDITOR, this.chopper, this.zoan, 3);

		assertThat(answer.version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void anotherEditorMayNotSubmitTheDraft() {
		assertThatThrownBy(() -> this.service.submit(EDITOR, this.chopper, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void aDraftWhoCannotSeeItCannotSubmitIt() {
		assertThatThrownBy(() -> this.service.submit(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@ParameterizedTest
	@EnumSource(names = { "IN_REVIEW", "REJECTED", "READY_TO_PUBLISH", "PUBLISHED" })
	void onlyADraftIsSubmitted(VersionStatus status) {
		UUID other = content();
		version(other, 1, status, this.nami, complete("Kodai"));
		stored();

		assertThatThrownBy(() -> this.service.submit(EDITOR, this.nami, other, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void aVersionPulledBackIsADraftOfItsAuthorAgainAndReviewersNoLongerSeeIt() {
		this.service.submit(EDITOR, this.nami, this.logia, 1);
		stored();

		var answer = this.service.pullBack(EDITOR, this.nami, this.logia, 1);
		stored();

		assertThat(answer.version().status()).isEqualTo(DRAFT);
		assertThat(answer.version().updatedAt()).isEqualTo(NOW);
		assertThat(answer.allowedActions()).containsExactly(EDIT, DELETE, SUBMIT);
		assertThatThrownBy(() -> this.service.getVersion(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void aClaimedVersionCannotBePulledBack() {
		UUID claimed = content();
		version(claimed, 1, IN_REVIEW, this.nami, complete("Kodai"), this.zoro);
		stored();

		assertThatThrownBy(() -> this.service.pullBack(EDITOR, this.nami, claimed, 1))
			.isInstanceOf(VersionActionConflictException.class);
		assertThat(this.service.getVersion(EDITOR, this.nami, claimed, 1).version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void anotherEditorMayNotPullTheVersionBack() {
		this.service.submit(EDITOR, this.nami, this.logia, 1);
		stored();

		assertThatThrownBy(() -> this.service.pullBack(EDITOR, this.chopper, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void onlyAVersionInReviewIsPulledBack() {
		assertThatThrownBy(() -> this.service.pullBack(EDITOR, this.nami, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void submittingAndPullingBackAreRecordedOnTheVersion() {
		this.service.submit(EDITOR, this.nami, this.logia, 1);
		this.service.pullBack(EDITOR, this.nami, this.logia, 1);
		stored();

		List<VersionEvent> events = this.service.events(EDITOR, this.logia, 1);
		assertThat(events).extracting(VersionEvent::action).containsExactly("VERSION_SUBMITTED", "VERSION_PULLED_BACK");
		assertThat(events).extracting(VersionEvent::actor).containsOnly(this.nami);
	}

	private void assertStillADraft(UUID contentId) {
		assertStillADraft(contentId, 1);
	}

	private void assertStillADraft(UUID contentId, int number) {
		stored();
		assertThat(this.service.getVersion(EDITOR, this.nami, contentId, number).version().status()).isEqualTo(DRAFT);
	}

	private static List<String> fieldsOf(ApplicationException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

	/**
	 * A body complete in both languages of the catalog, its names derived from the
	 * romaji.
	 */
	private static DevilFruitType complete(String romaji) {
		return complete(romaji, romaji);
	}

	private static DevilFruitType complete(String romaji, String name) {
		return new DevilFruitType(romaji, Map.of("it", translation(name + " IT", name + " descrizione"), "en",
				translation(name + " EN", name + " description")));
	}

	/** Advantages and disadvantages filled: a test names only the fields it is about. */
	private static DevilFruitTypeTranslation translation(String name, String description) {
		return new DevilFruitTypeTranslation(name, description, "Pro", "Contro");
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

	private void version(UUID contentId, int number, VersionStatus status, User author, DevilFruitType body) {
		version(contentId, number, status, author, body, null);
	}

	/** Seeds one version, based on the previous one, saying what is given. */
	private void version(UUID contentId, int number, VersionStatus status, User author, DevilFruitType body,
			User claimant) {
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(author))
			.claimant(claimant == null ? null : UserMapper.toEmbeddable(claimant))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new DevilFruitTypeVersionEntity(workflow);
		version.setRomaji(body.romaji());
		body.translations()
			.forEach((language, translation) -> version.getTranslations()
				.put(language, new TranslationEmbeddable(translation.name(), translation.description(),
						translation.advantages(), translation.disadvantages())));
		this.versionRepository.save(version);
	}

}
