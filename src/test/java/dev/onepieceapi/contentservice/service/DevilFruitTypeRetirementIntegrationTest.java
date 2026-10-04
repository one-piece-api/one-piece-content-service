package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RESTORE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RETIRE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * A publisher taking the online version offline (UF-CNT-10) and putting an older one back
 * online (UF-CNT-09) against a real PostgreSQL (Testcontainers).
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Logia - v1 by nami superseded, v2 by chopper online, v3 by nami a draft</li>
 * <li>Zoan - v1 by nami online</li>
 * <li>Kodai - v1 by nami retired, v2 by chopper superseded, nothing online</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeRetirementIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH,
			Permission.CONTENT_RETIRE);

	private static final Set<Permission> PUBLISHER_WHO_MAY_NOT_RETIRE = Set.of(Permission.CONTENT_READ,
			Permission.CONTENT_PUBLISH);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

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

	private UUID zoan;

	private UUID kodai;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		this.service = new DevilFruitTypeService(this.versionRepository, this.contentVersionRepository,
				this.contentRepository, validator, auditLogService, clock);

		this.logia = content();
		version(this.logia, 1, SUPERSEDED, this.nami, "Logia");
		version(this.logia, 2, PUBLISHED, this.chopper, "Logia Prime");
		version(this.logia, 3, DRAFT, this.nami, "Logia Next");
		this.zoan = content();
		version(this.zoan, 1, PUBLISHED, this.nami, "Zoan");
		this.kodai = content();
		version(this.kodai, 1, RETIRED, this.nami, "Kodai");
		version(this.kodai, 2, SUPERSEDED, this.chopper, "Kodai Prime");
		stored();
	}

	@Test
	void aPublisherRetiresTheOnlineVersionLeavingNothingOnline() {
		var answer = this.service.retire(PUBLISHER, this.vivi, this.zoan, 1);
		stored();

		Version<DevilFruitType> retired = this.service.getVersion(PUBLISHER, this.vivi, this.zoan, 1).version();
		assertThat(retired.status()).isEqualTo(RETIRED);
		assertThat(retired.updatedAt()).isEqualTo(NOW);
		assertThat(answer.version().status()).isEqualTo(RETIRED);
		assertThat(answer.allowedActions()).containsExactly(RESTORE);
		assertThat(this.versionRepository.findOnline(this.zoan)).isEmpty();
	}

	@Test
	void retiringIsRecordedOnTheVersionWithoutDetail() {
		this.service.retire(PUBLISHER, this.vivi, this.zoan, 1);
		stored();

		var events = this.service.events(PUBLISHER, this.zoan, 1);
		assertThat(events).extracting(VersionEvent::action).containsExactly("VERSION_RETIRED");
		assertThat(events).extracting(VersionEvent::actor).containsExactly(this.vivi);
		assertThat(events).extracting(VersionEvent::detail).containsOnlyNulls();
	}

	@Test
	void retiringTakesItsOwnPermissionNotTheOneToPublish() {
		assertThatThrownBy(() -> this.service.retire(PUBLISHER_WHO_MAY_NOT_RETIRE, this.vivi, this.zoan, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.retire(EDITOR, this.chopper, this.zoan, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThat(this.service.getVersion(PUBLISHER_WHO_MAY_NOT_RETIRE, this.vivi, this.zoan, 1).allowedActions())
			.isEmpty();
	}

	@Test
	void onlyTheOnlineVersionIsRetired() {
		assertThatThrownBy(() -> this.service.retire(PUBLISHER, this.vivi, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
		assertThatThrownBy(() -> this.service.retire(PUBLISHER, this.vivi, this.kodai, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void aPublisherRestoresASupersededVersionSupersedingTheOnlineOne() {
		var answer = this.service.restore(PUBLISHER, this.vivi, this.logia, 1);
		stored();

		Version<DevilFruitType> restored = this.service.getVersion(PUBLISHER, this.vivi, this.logia, 1).version();
		assertThat(restored.status()).isEqualTo(PUBLISHED);
		assertThat(restored.updatedAt()).isEqualTo(NOW);
		assertThat(restored.body().romaji()).isEqualTo("Logia");
		assertThat(answer.allowedActions()).containsExactly(RETIRE);
		Version<DevilFruitType> replaced = this.service.getVersion(PUBLISHER, this.vivi, this.logia, 2).version();
		assertThat(replaced.status()).isEqualTo(SUPERSEDED);
		assertThat(replaced.updatedAt()).isEqualTo(NOW);
	}

	@Test
	void restoringIsRecordedOnBothVersionsTheReplacedOneSayingByWhich() {
		this.service.restore(PUBLISHER, this.vivi, this.logia, 1);
		stored();

		var restored = this.service.events(PUBLISHER, this.logia, 1);
		assertThat(restored).extracting(VersionEvent::action).containsExactly("VERSION_RESTORED");
		assertThat(restored).extracting(VersionEvent::detail).containsOnlyNulls();
		var replaced = this.service.events(PUBLISHER, this.logia, 2);
		assertThat(replaced).extracting(VersionEvent::action).containsExactly("VERSION_SUPERSEDED");
		assertThat(replaced).extracting(VersionEvent::actor).containsExactly(this.vivi);
		assertThat(replaced).extracting(VersionEvent::detail).containsExactly("1");
	}

	@Test
	void restoringCreatesNoVersionAndLeavesTheOpenOneAsItIs() {
		this.service.restore(PUBLISHER, this.vivi, this.logia, 1);
		stored();

		assertThat(this.contentVersionRepository.findLatestNumber(this.logia)).isEqualTo(3);
		Version<DevilFruitType> draft = this.service.getVersion(EDITOR, this.nami, this.logia, 3).version();
		assertThat(draft.status()).isEqualTo(DRAFT);
		assertThat(draft.updatedAt()).isEqualTo(EARLIER);
	}

	@Test
	void aRetiredVersionIsRestoredWithNothingToSupersede() {
		var answer = this.service.restore(PUBLISHER, this.vivi, this.kodai, 1);
		stored();

		assertThat(answer.version().status()).isEqualTo(PUBLISHED);
		Version<DevilFruitType> other = this.service.getVersion(PUBLISHER, this.vivi, this.kodai, 2).version();
		assertThat(other.status()).isEqualTo(SUPERSEDED);
		assertThat(other.updatedAt()).isEqualTo(EARLIER);
		assertThat(this.service.events(PUBLISHER, this.kodai, 2)).isEmpty();
	}

	@Test
	void aRetiredVersionComesBackOnlineAndCanBeRetiredAgain() {
		this.service.retire(PUBLISHER, this.vivi, this.zoan, 1);
		stored();
		this.service.restore(PUBLISHER, this.vivi, this.zoan, 1);
		stored();

		assertThat(this.service.retire(PUBLISHER, this.vivi, this.zoan, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void onlyASupersededOrRetiredVersionIsRestored() {
		assertThatThrownBy(() -> this.service.restore(PUBLISHER, this.vivi, this.zoan, 1))
			.isInstanceOf(VersionActionConflictException.class);
		assertThatThrownBy(() -> this.service.restore(PUBLISHER, this.vivi, this.logia, 3))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void editorsAndReviewersSeeAnOlderVersionButMayNotRestoreIt() {
		assertThatThrownBy(() -> this.service.restore(EDITOR, this.chopper, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.restore(REVIEWER, this.zoro, this.kodai, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void aVersionThatDoesNotExistIsNotFound() {
		assertThatThrownBy(() -> this.service.retire(PUBLISHER, this.vivi, this.zoan, 7))
			.isInstanceOf(VersionNotFoundException.class);
		assertThatThrownBy(() -> this.service.restore(PUBLISHER, this.vivi, UUID.randomUUID(), 1))
			.isInstanceOf(VersionNotFoundException.class);
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
	 * Seeds a version of a content, complete in both languages of the catalog; any but
	 * the first is based on the one before.
	 */
	private void version(UUID contentId, int number, VersionStatus status, User author, String romaji) {
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
