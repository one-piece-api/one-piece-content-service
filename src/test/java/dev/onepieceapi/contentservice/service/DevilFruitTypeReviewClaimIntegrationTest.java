package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.RulesProperties;
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
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.APPROVE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.CLAIM;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.REJECT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RELEASE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * A reviewer claiming a version in review and releasing it (UF-CNT-13, UF-CNT-14) against
 * a real PostgreSQL (Testcontainers): who may, from which state, and what the optimistic
 * lock and the claim constraint of {@code V4} guarantee.
 * <p>
 * Seeded for every test, unclaimed:
 * <ul>
 * <li>Logia, by nami - v1 in review</li>
 * <li>Paramecia, by law - v1 in review</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeReviewClaimIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> EDITOR_REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private final User law = new User(UUID.randomUUID(), "law", "law@onepiece.local");

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
		version(this.logia, IN_REVIEW, this.nami, "Logia", null);
		this.paramecia = content();
		version(this.paramecia, IN_REVIEW, this.law, "Paramecia", null);
		stored();
	}

	@Test
	void aClaimedVersionIsHeldByItsReviewerWhoMayNowDecideOrLetItGo() {
		var answer = this.service.claim(REVIEWER, this.zoro, this.logia, 1);
		stored();

		var version = this.service.getVersion(REVIEWER, this.zoro, this.logia, 1).version();
		assertThat(version.claimant()).isEqualTo(this.zoro);
		assertThat(version.status()).isEqualTo(IN_REVIEW);
		assertThat(version.updatedAt()).isEqualTo(EARLIER);
		assertThat(answer.version().claimant()).isEqualTo(this.zoro);
		assertThat(answer.allowedActions()).containsExactly(RELEASE, APPROVE, REJECT);
	}

	@Test
	void everyoneWhoSeesAClaimedVersionSeesWhoHoldsItAndNobodyElseMayTakeIt() {
		this.service.claim(REVIEWER, this.zoro, this.logia, 1);
		stored();

		var seenByLaw = this.service.getVersion(EDITOR_REVIEWER, this.law, this.logia, 1);
		assertThat(seenByLaw.version().claimant()).isEqualTo(this.zoro);
		assertThat(seenByLaw.allowedActions()).isEmpty();
		assertThat(this.service.getVersion(EDITOR, this.nami, this.logia, 1).version().claimant()).isEqualTo(this.zoro);
	}

	@Test
	void aVersionAlreadyClaimedCannotBeClaimedAgain() {
		this.service.claim(REVIEWER, this.zoro, this.logia, 1);
		stored();

		assertThatThrownBy(() -> this.service.claim(EDITOR_REVIEWER, this.law, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
		assertThatThrownBy(() -> this.service.claim(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void nobodyClaimsTheirOwnVersion() {
		assertThat(this.service.getVersion(EDITOR_REVIEWER, this.law, this.paramecia, 1).allowedActions())
			.doesNotContain(CLAIM);
		assertThatThrownBy(() -> this.service.claim(EDITOR_REVIEWER, this.law, this.paramecia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void anEditorWithoutReviewMayNotClaim() {
		assertThatThrownBy(() -> this.service.claim(EDITOR, this.nami, this.paramecia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "PUBLISHED" })
	void onlyAVersionInReviewIsClaimed(VersionStatus status) {
		UUID other = content();
		version(other, status, this.nami, "Kodai", null);
		stored();

		assertThatThrownBy(() -> this.service.claim(EDITOR_REVIEWER, this.law, other, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void aDraftIsNotEvenSeenByAReviewer() {
		UUID draft = content();
		version(draft, DRAFT, this.nami, "Kodai", null);
		stored();

		assertThatThrownBy(() -> this.service.claim(REVIEWER, this.zoro, draft, 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void aReleasedVersionIsFreeForAnyReviewerAgain() {
		this.service.claim(REVIEWER, this.zoro, this.logia, 1);
		stored();

		var answer = this.service.release(REVIEWER, this.zoro, this.logia, 1);
		stored();

		assertThat(answer.version().isClaimed()).isFalse();
		assertThat(answer.allowedActions()).containsExactly(CLAIM);
		assertThat(this.service.getVersion(EDITOR_REVIEWER, this.law, this.logia, 1).allowedActions())
			.containsExactly(CLAIM);
	}

	@Test
	void onlyTheReviewerHoldingAVersionMayReleaseIt() {
		this.service.claim(REVIEWER, this.zoro, this.logia, 1);
		stored();

		assertThatThrownBy(() -> this.service.release(EDITOR_REVIEWER, this.law, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void anUnclaimedVersionHasNothingToRelease() {
		assertThatThrownBy(() -> this.service.release(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void claimingAndReleasingAreRecordedOnTheVersion() {
		this.service.claim(REVIEWER, this.zoro, this.logia, 1);
		this.service.release(REVIEWER, this.zoro, this.logia, 1);
		stored();

		List<VersionEvent> events = this.service.events(REVIEWER, this.logia, 1);
		assertThat(events).extracting(VersionEvent::action).containsExactly("VERSION_CLAIMED", "VERSION_RELEASED");
		assertThat(events).extracting(VersionEvent::actor).containsOnly(this.zoro);
	}

	@Test
	void aClaimWrittenFromAnOutdatedReadFailsInsteadOfOverwritingTheOtherClaim() {
		// zoro reads the version, unclaimed...
		this.versionRepository.findById(versionIdOf(this.logia)).orElseThrow();
		// ...law claims it and commits in the meantime, bumping the lock...
		this.entityManager.getEntityManager().createNativeQuery("""
				update content_version
				set claimant_user_id = ?1, claimant_username = 'law', claimant_email = 'law@onepiece.local',
					lock_version = lock_version + 1
				where content_id = ?2""").setParameter(1, this.law.id()).setParameter(2, this.logia).executeUpdate();

		// ...so zoro's claim, decided on what he read, cannot be written.
		this.service.claim(REVIEWER, this.zoro, this.logia, 1);
		assertThatThrownBy(this.versionRepository::flush).isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void theDatabaseRefusesAClaimOutsideReview() {
		UUID draft = content();
		version(draft, DRAFT, this.nami, "Kodai", this.zoro);

		assertThatThrownBy(this.versionRepository::flush).isInstanceOf(DataIntegrityViolationException.class);
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID versionIdOf(UUID contentId) {
		return this.versionRepository.findOthers(contentId, 0).getFirst().getVersionId();
	}

	private UUID content() {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT_TYPE, EARLIER))
			.getId();
	}

	/**
	 * Seeds the first version of a content, complete in both languages of the catalog.
	 */
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
