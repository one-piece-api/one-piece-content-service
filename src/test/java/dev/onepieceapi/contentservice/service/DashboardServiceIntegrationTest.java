package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.DashboardProperties;
import dev.onepieceapi.contentservice.domain.dashboard.Activity;
import dev.onepieceapi.contentservice.domain.dashboard.StatusCount;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.REJECTED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The dashboard (UF-CNT-19) against a real PostgreSQL (Testcontainers).
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Logia - v1 by nami superseded, v2 by nami online, v3 by chopper in review, held by
 * zoro</li>
 * <li>Zoan - v1 by nami archived, v2 by nami archived, v3 by nami a draft</li>
 * <li>Kodai - v1 by chopper in review, unclaimed</li>
 * <li>Mythic - v1 by chopper rejected</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DashboardServiceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant EARLIER = Instant.parse("2026-10-01T10:00:00Z");

	private static final int ACTIVITY_SIZE = 3;

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> READER = Set.of(Permission.CONTENT_READ);

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
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TestEntityManager entityManager;

	private DashboardService service;

	private UUID logia;

	private UUID zoan;

	private UUID kodai;

	private UUID mythic;

	@BeforeEach
	void setUp() {
		this.service = new DashboardService(this.contentVersionRepository, this.contentRepository,
				this.auditLogRepository, List.of(new DevilFruitTypeTitleSource(this.versionRepository)),
				new DashboardProperties(ACTIVITY_SIZE));

		this.logia = content();
		version(this.logia, 1, SUPERSEDED, this.nami, null, "Logia");
		version(this.logia, 2, PUBLISHED, this.nami, null, "Logia");
		version(this.logia, 3, IN_REVIEW, this.chopper, this.zoro, "Logia Prime");
		this.zoan = content();
		version(this.zoan, 1, ARCHIVED, this.nami, null, "Zoan");
		version(this.zoan, 2, ARCHIVED, this.nami, null, "Zoan");
		version(this.zoan, 3, DRAFT, this.nami, null, "Zoan");
		this.kodai = content();
		version(this.kodai, 1, IN_REVIEW, this.chopper, null, "Kodai");
		this.mythic = content();
		version(this.mythic, 1, REJECTED, this.chopper, null, "Mythic");
		stored();
	}

	@Test
	void anEditorCountsContentsPerStatusInWorkflowOrderZeroIncludedWithoutSuperseded() {
		var counts = this.service.statusCounts(EDITOR, this.nami);

		assertThat(counts).extracting(StatusCount::status)
			.containsExactly(DRAFT, IN_REVIEW, REJECTED, READY_TO_PUBLISH, PUBLISHED, ARCHIVED, RETIRED);
		assertThat(counts).extracting(StatusCount::contents).containsExactly(1L, 2L, 1L, 0L, 1L, 1L, 0L);
	}

	@Test
	void aContentWithSeveralVersionsInAStatusCountsOnce() {
		var archived = count(this.service.statusCounts(READER, this.nami), ARCHIVED);

		assertThat(archived.contents()).isEqualTo(1);
	}

	@Test
	void anEditorsShareIsTheirDraftsOnly() {
		var counts = this.service.statusCounts(EDITOR, this.nami);

		assertThat(count(counts, DRAFT).mine()).isEqualTo(1);
		assertThat(count(this.service.statusCounts(EDITOR, this.chopper), DRAFT).mine()).isZero();
		assertThat(count(counts, IN_REVIEW).mine()).as("an editor holds no review").isNull();
		assertThat(count(counts, PUBLISHED).mine()).isNull();
	}

	@Test
	void aReviewerSeesNoDraftOrRejectedAndTheirShareIsTheReviewsTheyHold() {
		var counts = this.service.statusCounts(REVIEWER, this.zoro);

		assertThat(counts).extracting(StatusCount::status)
			.containsExactly(IN_REVIEW, READY_TO_PUBLISH, PUBLISHED, ARCHIVED, RETIRED);
		assertThat(count(counts, IN_REVIEW).contents()).isEqualTo(2);
		assertThat(count(counts, IN_REVIEW).mine()).isEqualTo(1);
	}

	@Test
	void aReaderSeesOnlyClosedStatusesAndWhatIsReadyToPublish() {
		assertThat(this.service.statusCounts(READER, this.nami)).extracting(StatusCount::status)
			.containsExactly(READY_TO_PUBLISH, PUBLISHED, ARCHIVED, RETIRED);
	}

	@Test
	void theActivityIsTheCallersOwnMostRecentFirstAndLimited() {
		record(this.nami, "VERSION_CREATED", this.zoan, 3, "Zoan", 1);
		record(this.chopper, "VERSION_SUBMITTED", this.kodai, 1, "Kodai", 2);
		record(this.nami, "VERSION_PUBLISHED", this.logia, 2, "Logia", 3);
		record(this.nami, "VERSION_ARCHIVED", this.zoan, 2, "Zoan", 4);
		record(this.nami, "VERSION_ARCHIVED", this.zoan, 1, "Zoan", 5);
		recordWithoutContent(this.nami, "LANGUAGE_ADDED", 6);
		stored();

		var activity = this.service.activity(EDITOR, this.nami);

		assertThat(activity).extracting(Activity::action)
			.containsExactly("VERSION_ARCHIVED", "VERSION_ARCHIVED", "VERSION_PUBLISHED");
		assertThat(activity).extracting(Activity::versionNumber).containsExactly(1, 2, 2);
		assertThat(activity).extracting(Activity::entityType).containsOnly(EntityType.DEVIL_FRUIT_TYPE);
	}

	@Test
	void anActionIsNamedAsItsContentIsCalledNowByItsMostRecentVisibleVersion() {
		record(this.chopper, "VERSION_SUBMITTED", this.logia, 3, "Logia Prime", 1);
		stored();

		var asEditor = this.service.activity(EDITOR, this.chopper).getFirst();
		assertThat(asEditor.title().names()).containsEntry("it", "Logia Prime IT")
			.containsEntry("en", "Logia Prime EN");
		assertThat(asEditor.title().fallback()).isEqualTo("Logia Prime");

		var asReader = this.service.activity(READER, this.chopper).getFirst();
		assertThat(asReader.title().fallback()).as("the review is not visible to a reader").isEqualTo("Logia");
		assertThat(asReader.label()).isEqualTo("Logia Prime");
	}

	@Test
	void aContentTheCallerNoLongerSeesKeepsItsLabelWithoutATitle() {
		record(this.zoro, "VERSION_REJECTED", this.mythic, 1, "Mythic", 1);
		stored();

		var rejected = this.service.activity(REVIEWER, this.zoro).getFirst();
		assertThat(rejected.title()).isNull();
		assertThat(rejected.label()).isEqualTo("Mythic");
		assertThat(rejected.contentId()).isEqualTo(this.mythic);
	}

	@Test
	void aDiscardedContentKeepsItsLabelWithoutTypeVersionOrTitle() {
		UUID gone = UUID.randomUUID();
		this.auditLogRepository.save(AuditLogEntity.builder()
			.action("VERSION_DELETED")
			.actor(UserMapper.toEmbeddable(this.nami))
			.targetContentId(gone)
			.targetVersionId(UUID.randomUUID())
			.targetLabel("Paramecia")
			.occurredAt(EARLIER)
			.build());
		stored();

		var deleted = this.service.activity(EDITOR, this.nami).getFirst();
		assertThat(deleted.entityType()).isNull();
		assertThat(deleted.versionNumber()).isNull();
		assertThat(deleted.title()).isNull();
		assertThat(deleted.label()).isEqualTo("Paramecia");
	}

	private static StatusCount count(List<StatusCount> counts, VersionStatus status) {
		return counts.stream().filter(count -> count.status() == status).findFirst().orElseThrow();
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

	private void version(UUID contentId, int number, VersionStatus status, User author, User claimant, String romaji) {
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
		var body = new DevilFruitType(romaji, Map.of("it", new DevilFruitTypeTranslation(romaji + " IT", "descrizione"),
				"en", new DevilFruitTypeTranslation(romaji + " EN", "description")));
		version.setRomaji(body.romaji());
		body.translations()
			.forEach((language, translation) -> version.getTranslations()
				.put(language, new TranslationEmbeddable(translation.name(), translation.description())));
		this.versionRepository.save(version);
	}

	/** An audit record by {@code actor} on a version, {@code minutes} after the seed. */
	private void record(User actor, String action, UUID contentId, int number, String label, int minutes) {
		UUID versionId = versionIdOf(contentId, number);
		this.auditLogRepository.save(AuditLogEntity.builder()
			.action(action)
			.actor(UserMapper.toEmbeddable(actor))
			.targetContentId(contentId)
			.targetVersionId(versionId)
			.targetLabel(label)
			.occurredAt(EARLIER.plus(Duration.ofMinutes(minutes)))
			.build());
	}

	private void recordWithoutContent(User actor, String action, int minutes) {
		this.auditLogRepository.save(AuditLogEntity.builder()
			.action(action)
			.actor(UserMapper.toEmbeddable(actor))
			.targetLabel("fr")
			.occurredAt(EARLIER.plus(Duration.ofMinutes(minutes)))
			.build());
	}

	private UUID versionIdOf(UUID contentId, int number) {
		return this.entityManager.getEntityManager()
			.createQuery(
					"select v.id from ContentVersionEntity v where v.contentId = :contentId and v.versionNumber = :number",
					UUID.class)
			.setParameter("contentId", contentId)
			.setParameter("number", number)
			.getSingleResult();
	}

}
