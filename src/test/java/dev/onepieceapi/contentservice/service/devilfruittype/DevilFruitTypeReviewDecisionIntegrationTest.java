package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
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

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.ARCHIVE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.DELETE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.EDIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RETURN_TO_DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.SUBMIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The claimant approving or rejecting a version in review, and its author taking a
 * rejected one back to draft (UF-CNT-05, UF-CNT-06, UF-CNT-15), against a real PostgreSQL
 * (Testcontainers).
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Logia, by nami - v1 in review, claimed by zoro</li>
 * <li>Paramecia, by nami - v1 rejected by zoro</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeReviewDecisionIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final String REASON = "The English name is missing a source";

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> EDITOR_REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private final User law = new User(UUID.randomUUID(), "law", "law@onepiece.local");

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	@Autowired
	private DevilFruitTypeVersionRepository versionRepository;

	@Autowired
	private ContentVersionRepository contentVersionRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private DevilFruitVersionRepository fruitRepository;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TestEntityManager entityManager;

	private DevilFruitTypeService service;

	private UUID logia;

	private UUID paramecia;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		this.service = new DevilFruitTypeService(this.versionRepository, this.contentVersionRepository,
				this.contentRepository, validator,
				new DevilFruitTypeRules(this.contentRepository, this.fruitRepository, new RulesProperties(5)),
				auditLogService, clock);

		this.logia = content();
		version(this.logia,
				ContentVersionEntity.builder().status(IN_REVIEW).claimant(UserMapper.toEmbeddable(this.zoro)), "Logia");
		this.paramecia = content();
		version(this.paramecia, ContentVersionEntity.builder().status(REJECTED).rejectionReason(REASON), "Paramecia");
		stored();
	}

	@Test
	void anApprovedVersionIsReadyToPublishUnclaimedAndReachesThePublishers() {
		var answer = this.service.approve(REVIEWER, this.zoro, this.logia, 1);
		stored();

		var version = this.service.getVersion(PUBLISHER, this.vivi, this.logia, 1);
		assertThat(version.version().status()).isEqualTo(READY_TO_PUBLISH);
		assertThat(version.version().isClaimed()).isFalse();
		assertThat(version.version().updatedAt()).isEqualTo(NOW);
		assertThat(version.allowedActions()).containsExactly(PUBLISH, ARCHIVE);
		assertThat(answer.allowedActions()).isEmpty();
	}

	@Test
	void aRejectedVersionKeepsItsReasonIsUnclaimedAndOnlyItsAuthorMayTakeItBack() {
		var answer = this.service.reject(REVIEWER, this.zoro, this.logia, 1, REASON);
		stored();

		var version = this.service.getVersion(EDITOR, this.nami, this.logia, 1);
		assertThat(version.version().status()).isEqualTo(REJECTED);
		assertThat(version.version().rejectionReason()).isEqualTo(REASON);
		assertThat(version.version().isClaimed()).isFalse();
		assertThat(version.version().updatedAt()).isEqualTo(NOW);
		assertThat(version.allowedActions()).containsExactly(RETURN_TO_DRAFT);
		assertThat(this.service.getVersion(EDITOR, this.chopper, this.logia, 1).allowedActions()).isEmpty();
		assertThat(answer.allowedActions()).isEmpty();
	}

	@Test
	void aRejectedVersionLeavesTheSightOfReviewersAndPublishers() {
		this.service.reject(REVIEWER, this.zoro, this.logia, 1, REASON);
		stored();

		assertThatThrownBy(() -> this.service.getVersion(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionNotFoundException.class);
		assertThatThrownBy(() -> this.service.getVersion(PUBLISHER, this.vivi, this.logia, 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void onlyTheReviewerHoldingAVersionDecidesOnIt() {
		assertThatThrownBy(() -> this.service.approve(EDITOR_REVIEWER, this.law, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.reject(EDITOR_REVIEWER, this.law, this.logia, 1, REASON))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void nobodyDecidesOnAnUnclaimedVersion() {
		this.service.release(REVIEWER, this.zoro, this.logia, 1);
		stored();

		assertThatThrownBy(() -> this.service.approve(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.reject(REVIEWER, this.zoro, this.logia, 1, REASON))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void decidingAndTakingBackAreRecordedWithTheReasonOfTheRejection() {
		this.service.reject(REVIEWER, this.zoro, this.logia, 1, REASON);
		this.service.returnToDraft(EDITOR, this.nami, this.logia, 1);
		stored();

		List<VersionEvent> events = this.service.events(EDITOR, this.logia, 1);
		assertThat(events).extracting(VersionEvent::action)
			.containsExactly("VERSION_REJECTED", "VERSION_RETURNED_TO_DRAFT");
		assertThat(events).extracting(VersionEvent::actor).containsExactly(this.zoro, this.nami);
		assertThat(events).extracting(VersionEvent::detail).containsExactly(REASON, null);
	}

	@Test
	void anApprovalIsRecordedOnTheVersion() {
		this.service.approve(REVIEWER, this.zoro, this.logia, 1);
		stored();

		List<VersionEvent> events = this.service.events(PUBLISHER, this.logia, 1);
		assertThat(events).extracting(VersionEvent::action).containsExactly("VERSION_APPROVED");
		assertThat(events).extracting(VersionEvent::actor).containsExactly(this.zoro);
	}

	@Test
	void aVersionTakenBackIsADraftOfItsAuthorStillSayingWhatToFix() {
		var answer = this.service.returnToDraft(EDITOR, this.nami, this.paramecia, 1);
		stored();

		var version = this.service.getVersion(EDITOR, this.nami, this.paramecia, 1).version();
		assertThat(version.status()).isEqualTo(DRAFT);
		assertThat(version.rejectionReason()).isEqualTo(REASON);
		assertThat(version.updatedAt()).isEqualTo(NOW);
		assertThat(answer.allowedActions()).containsExactly(EDIT, DELETE, SUBMIT);
	}

	@Test
	void resubmittingLeavesTheReasonOfTheFailedRoundBehind() {
		this.service.returnToDraft(EDITOR, this.nami, this.paramecia, 1);
		this.service.submit(EDITOR, this.nami, this.paramecia, 1);
		stored();

		assertThat(this.service.getVersion(EDITOR, this.nami, this.paramecia, 1).version().rejectionReason()).isNull();
	}

	@Test
	void onlyTheAuthorTakesARejectedVersionBack() {
		assertThatThrownBy(() -> this.service.returnToDraft(EDITOR, this.chopper, this.paramecia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void onlyARejectedVersionIsTakenBackToDraft() {
		assertThatThrownBy(() -> this.service.returnToDraft(EDITOR, this.nami, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void aRejectedVersionIsNeitherEditedNorDiscardedBeforeGoingBackToDraft() {
		var body = new DevilFruitType("Paramecia", Map.of());

		assertThatThrownBy(() -> this.service.edit(EDITOR, this.nami, this.paramecia, 1, body))
			.isInstanceOf(VersionActionConflictException.class);
		assertThatThrownBy(() -> this.service.delete(EDITOR, this.nami, this.paramecia, 1))
			.isInstanceOf(VersionActionConflictException.class);
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

	/**
	 * Seeds the first version of a content by nami, complete in both languages of the
	 * catalog, with the workflow given.
	 */
	private void version(UUID contentId, ContentVersionEntity.ContentVersionEntityBuilder workflow, String romaji) {
		var version = new DevilFruitTypeVersionEntity(workflow.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(this.nami))
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build());
		var body = new DevilFruitType(romaji,
				Map.of("it", new DevilFruitTypeTranslation(romaji + " IT", "descrizione", "vantaggi", "svantaggi"),
						"en",
						new DevilFruitTypeTranslation(romaji + " EN", "description", "advantages", "disadvantages")));
		version.setRomaji(body.romaji());
		body.translations()
			.forEach((language, translation) -> version.getTranslations()
				.put(language, new TranslationEmbeddable(translation.name(), translation.description(),
						translation.advantages(), translation.disadvantages())));
		this.versionRepository.save(version);
	}

}
