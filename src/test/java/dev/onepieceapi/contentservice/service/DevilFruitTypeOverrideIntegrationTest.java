package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
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
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.CLAIM;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.DELETE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.EDIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RELEASE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.SUBMIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The administrative override (docs/user-flows/content-editorial-workflow.md 2.3,
 * UF-CNT-20) against a real PostgreSQL (Testcontainers): an administrator unblocks an
 * abandoned draft or a forgotten claim and carries a content alone from draft to online -
 * authorship never rewritten, every override recorded as such ({@code V5}).
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Logia, by nami - v1 in draft</li>
 * <li>Paramecia, by nami - v1 in review, held by zoro</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeOverrideIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

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

	private UUID logia;

	private UUID paramecia;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		this.service = new DevilFruitTypeService(this.versionRepository, this.contentVersionRepository,
				this.contentRepository, validator, auditLogService, clock);

		this.logia = content();
		version(this.logia, DRAFT, this.nami, "Logia", null);
		this.paramecia = content();
		version(this.paramecia, IN_REVIEW, this.nami, "Paramecia", this.zoro);
		stored();
	}

	@Test
	void anAdministratorIsToldWhichActionsOnSomeoneElsesDraftAreOverrides() {
		var seenByLuffy = this.service.getVersion(ADMIN, this.luffy, this.logia, 1);

		assertThat(seenByLuffy.allowedActions()).containsExactly(EDIT, DELETE, SUBMIT);
		assertThat(seenByLuffy.overrideActions()).containsExactly(EDIT, DELETE, SUBMIT);
		assertThat(this.service.getVersion(EDITOR, this.nami, this.logia, 1).overrideActions()).isEmpty();
	}

	@Test
	void anAdministratorEditsSomeoneElsesDraftWhoseAuthorDoesNotChange() {
		this.service.edit(ADMIN, this.luffy, this.logia, 1, body("Rogia"));
		stored();

		var version = this.service.getVersion(EDITOR, this.nami, this.logia, 1).version();
		assertThat(version.author()).isEqualTo(this.nami);
		assertThat(version.body().romaji()).isEqualTo("Rogia");
		assertThat(this.service.events(EDITOR, this.logia, 1)).last()
			.extracting(VersionEvent::action, VersionEvent::actor, VersionEvent::override)
			.containsExactly("VERSION_EDITED", this.luffy, true);
	}

	@Test
	void anAdministratorDiscardsAnAbandonedDraftAndTheRecordSaysSo() {
		UUID versionId = this.versionRepository.findOthers(this.logia, 0).getFirst().getVersionId();

		this.service.delete(ADMIN, this.luffy, this.logia, 1);
		stored();

		assertThat(this.entityManager.find(ContentEntity.class, this.logia)).isNull();
		assertThat(this.auditLogRepository.findByTargetVersionIdOrderByOccurredAtAscIdAsc(versionId))
			.extracting(AuditLogEntity::getAction, AuditLogEntity::isOverride)
			.containsExactly(tuple("VERSION_DELETED", true));
	}

	@Test
	void anAdministratorReleasesAForgottenClaimThenTakesItLikeAnyReviewer() {
		var released = this.service.release(ADMIN, this.luffy, this.paramecia, 1);
		stored();
		assertThat(released.version().isClaimed()).isFalse();
		assertThat(released.allowedActions()).contains(CLAIM);
		assertThat(released.overrideActions()).doesNotContain(CLAIM);

		this.service.claim(ADMIN, this.luffy, this.paramecia, 1);
		stored();

		assertThat(this.service.events(ADMIN, this.paramecia, 1))
			.extracting(VersionEvent::action, VersionEvent::detail, VersionEvent::override)
			.containsExactly(tuple("VERSION_RELEASED", "zoro", true), tuple("VERSION_CLAIMED", null, false));
	}

	@Test
	void anAdministratorNeverDecidesOnAVersionSomeoneElseHolds() {
		var seenByLuffy = this.service.getVersion(ADMIN, this.luffy, this.paramecia, 1);
		assertThat(seenByLuffy.allowedActions()).containsExactly(RELEASE);

		assertThatThrownBy(() -> this.service.approve(ADMIN, this.luffy, this.paramecia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.reject(ADMIN, this.luffy, this.paramecia, 1, "Not like this"))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void anAdministratorWithoutReviewMayNotClaimTheirOwnVersion() {
		var adminWithoutReview = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE, Permission.CONTENT_ADMIN);
		UUID created = this.service.create(adminWithoutReview, this.luffy, body("Zoan")).id();
		this.service.submit(adminWithoutReview, this.luffy, created, 1);
		stored();

		assertThatThrownBy(() -> this.service.claim(adminWithoutReview, this.luffy, created, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void anAdministratorCarriesTheirOwnContentFromDraftToOnlineAlone() {
		UUID created = this.service.create(ADMIN, this.luffy, body("Zoan")).id();
		this.service.submit(ADMIN, this.luffy, created, 1);
		this.service.claim(ADMIN, this.luffy, created, 1);
		this.service.approve(ADMIN, this.luffy, created, 1);
		var published = this.service.publish(ADMIN, this.luffy, created, 1);
		stored();

		assertThat(published.version().status()).isEqualTo(PUBLISHED);
		assertThat(this.service.events(ADMIN, created, 1)).extracting(VersionEvent::action, VersionEvent::override)
			.containsExactly(tuple("VERSION_CREATED", false), tuple("VERSION_SUBMITTED", false),
					tuple("VERSION_CLAIMED", true), tuple("VERSION_APPROVED", false),
					tuple("VERSION_PUBLISHED", false));
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

	/** Complete in both languages of the catalog. */
	private static DevilFruitType body(String romaji) {
		return new DevilFruitType(romaji, Map.of("it", new DevilFruitTypeTranslation(romaji + " IT", "descrizione"),
				"en", new DevilFruitTypeTranslation(romaji + " EN", "description")));
	}

	/** Seeds the first version of a content. */
	private void version(UUID contentId, VersionStatus status, User author, String romaji, User claimant) {
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(author))
			.claimant(UserMapper.toEmbeddable(claimant))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new DevilFruitTypeVersionEntity(workflow);
		DevilFruitType body = body(romaji);
		version.setRomaji(body.romaji());
		body.translations()
			.forEach((language, translation) -> version.getTranslations()
				.put(language, new TranslationEmbeddable(translation.name(), translation.description())));
		this.versionRepository.save(version);
	}

}
