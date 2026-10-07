package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
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
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Two callers changing the same content at the same instant, each in their own
 * transaction against a real PostgreSQL (Testcontainers), where only the database can
 * tell them apart. Giving the content an open version: two editors opening a new one
 * (UF-CNT-08), or a publisher recovering an archived one while an editor opens a new one
 * (UF-CNT-17) - both held right after checking that the content has no open version.
 * Putting a version online while nothing is: two publishers restoring different versions
 * (UF-CNT-09), or one restoring while another publishes (UF-CNT-07) - both held right
 * after finding nothing online to supersede. Either way both pass the check: the database
 * must then let only one of them in, and the other must be refused as a conflict, not
 * fail with a server error. Not run inside a test transaction: each caller commits, as in
 * production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DevilFruitTypeRaceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final long WAIT_SECONDS = 10;

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

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
	private PlatformTransactionManager transactionManager;

	@Test
	void ofTwoEditorsOpeningAtOnceOneGetsTheNewVersionAndTheOtherAConflict() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID logia = transaction.execute(status -> seedContent(VersionStatus.PUBLISHED));
		DevilFruitTypeService service = serviceWithBothCallersHeldAfterTheOpenVersionCheck(logia);

		List<Object> outcomes = race(
				() -> transaction.execute(status -> service.openNewVersion(EDITOR, this.nami, logia, 1)),
				() -> transaction.execute(status -> service.openNewVersion(EDITOR, this.chopper, logia, 1)));

		assertOneGotInAndTheOtherAConflict(outcomes);
		Integer latest = transaction.execute(status -> this.contentVersionRepository.findLatestNumber(logia));
		assertThat(latest).isEqualTo(2);
	}

	@Test
	void ofARecoveryAndANewVersionAtOnceOneGetsInAndTheOtherAConflict() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID zoan = transaction.execute(status -> seedContent(VersionStatus.ARCHIVED));
		DevilFruitTypeService service = serviceWithBothCallersHeldAfterTheOpenVersionCheck(zoan);

		List<Object> outcomes = race(
				() -> transaction.execute(status -> service.recover(PUBLISHER, this.vivi, zoan, 1)),
				() -> transaction.execute(status -> service.openNewVersion(EDITOR, this.chopper, zoan, 1)));

		assertOneGotInAndTheOtherAConflict(outcomes);
		Boolean recovered = transaction
			.execute(status -> this.versionRepository.findVisible(zoan, 1, Set.of(VersionStatus.READY_TO_PUBLISH))
				.isPresent());
		Integer latest = transaction.execute(status -> this.contentVersionRepository.findLatestNumber(zoan));
		assertThat(latest).isEqualTo(Boolean.TRUE.equals(recovered) ? 1 : 2);
	}

	@Test
	void aRecoveryThatMissedANewVersionOpenedMeanwhileIsRefusedAsAConflict() {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID zoan = transaction.execute(status -> seedContent(VersionStatus.ARCHIVED));
		transaction.executeWithoutResult(
				status -> serviceSeeingNoOpenVersion(zoan).openNewVersion(EDITOR, this.chopper, zoan, 1));
		DevilFruitTypeService late = serviceSeeingNoOpenVersion(zoan);

		assertThatThrownBy(() -> transaction.execute(status -> late.recover(PUBLISHER, this.vivi, zoan, 1)))
			.isInstanceOf(VersionActionConflictException.class);
		Boolean stillArchived = transaction
			.execute(status -> this.versionRepository.findVisible(zoan, 1, Set.of(VersionStatus.ARCHIVED)).isPresent());
		assertThat(stillArchived).isTrue();
	}

	@Test
	void ofTwoVersionsRestoredAtOnceWithNothingOnlineOneGoesOnlineAndTheOtherGetsAConflict() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID kodai = transaction.execute(status -> seedContent(VersionStatus.RETIRED, VersionStatus.SUPERSEDED));
		DevilFruitTypeService service = serviceWithBothCallersHeldAfterLookingForTheOnlineVersion(kodai);

		List<Object> outcomes = race(
				() -> transaction.execute(status -> service.restore(PUBLISHER, this.vivi, kodai, 1)),
				() -> transaction.execute(status -> service.restore(PUBLISHER, this.luffy, kodai, 2)));

		assertOneGotInAndTheOtherAConflict(outcomes);
		Boolean online = transaction.execute(status -> this.versionRepository.findOnline(kodai).isPresent());
		assertThat(online).isTrue();
	}

	@Test
	void ofARestoreAndAPublicationAtOnceWithNothingOnlineOneGoesOnlineAndTheOtherGetsAConflict() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID kodai = transaction.execute(status -> seedContent(VersionStatus.RETIRED, VersionStatus.READY_TO_PUBLISH));
		DevilFruitTypeService service = serviceWithBothCallersHeldAfterLookingForTheOnlineVersion(kodai);

		List<Object> outcomes = race(
				() -> transaction.execute(status -> service.restore(PUBLISHER, this.vivi, kodai, 1)),
				() -> transaction.execute(status -> service.publish(PUBLISHER, this.luffy, kodai, 2)));

		assertOneGotInAndTheOtherAConflict(outcomes);
		Boolean online = transaction.execute(status -> this.versionRepository.findOnline(kodai).isPresent());
		assertThat(online).isTrue();
	}

	private static void assertOneGotInAndTheOtherAConflict(List<Object> outcomes) {
		assertThat(outcomes).filteredOn(VersionAccess.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(VersionActionConflictException.class::isInstance).hasSize(1);
	}

	/**
	 * Runs the callers at once and gives what each got: its version, or why it was
	 * refused.
	 */
	@SafeVarargs
	private static List<Object> race(Callable<VersionAccess<DevilFruitType>>... callers) throws InterruptedException {
		List<Object> outcomes = new ArrayList<>();
		try (var executor = Executors.newFixedThreadPool(callers.length)) {
			for (Future<VersionAccess<DevilFruitType>> future : executor.invokeAll(List.of(callers))) {
				outcomes.add(outcomeOf(future));
			}
		}
		return outcomes;
	}

	/**
	 * The service, with a view of the versions answering that the content has no open
	 * version, as read before anyone acted: the same as two callers checking at once,
	 * without threads - so the one that comes second is always the late one.
	 */
	private DevilFruitTypeService serviceSeeingNoOpenVersion(UUID contentId) {
		ContentVersionRepository stale = mock(ContentVersionRepository.class,
				delegatesTo(this.contentVersionRepository));
		doReturn(false).when(stale).hasOpenVersion(contentId);
		return service(this.versionRepository, stale);
	}

	/**
	 * The service, with its view of the versions holding each caller right after it
	 * checked for an open version, until both have checked.
	 */
	private DevilFruitTypeService serviceWithBothCallersHeldAfterTheOpenVersionCheck(UUID contentId) {
		var bothChecked = new CyclicBarrier(2);
		ContentVersionRepository heldAfterCheck = mock(ContentVersionRepository.class,
				delegatesTo(this.contentVersionRepository));
		doAnswer(invocation -> {
			boolean open = this.contentVersionRepository.hasOpenVersion(contentId);
			bothChecked.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return open;
		}).when(heldAfterCheck).hasOpenVersion(contentId);
		return service(this.versionRepository, heldAfterCheck);
	}

	/**
	 * The service, with its view of the versions holding each caller right after it
	 * looked for the online version to supersede, until both have looked.
	 */
	private DevilFruitTypeService serviceWithBothCallersHeldAfterLookingForTheOnlineVersion(UUID contentId) {
		var bothLooked = new CyclicBarrier(2);
		DevilFruitTypeVersionRepository heldAfterLooking = mock(DevilFruitTypeVersionRepository.class,
				delegatesTo(this.versionRepository));
		doAnswer(invocation -> {
			var online = this.versionRepository.findOnline(contentId);
			bothLooked.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return online;
		}).when(heldAfterLooking).findOnline(contentId);
		return service(heldAfterLooking, this.contentVersionRepository);
	}

	private DevilFruitTypeService service(DevilFruitTypeVersionRepository versions,
			ContentVersionRepository contentVersions) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		return new DevilFruitTypeService(versions, contentVersions, this.contentRepository, validator,
				new DevilFruitTypeRules(this.contentRepository, this.fruitRepository, new RulesProperties(5)),
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

	/** What a caller got: the version, or why it was refused. */
	private static Object outcomeOf(Future<VersionAccess<DevilFruitType>> future) throws InterruptedException {
		try {
			return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
		}
		catch (ExecutionException ex) {
			return ex.getCause();
		}
		catch (TimeoutException ex) {
			return ex;
		}
	}

	/** A content by nami with a version in each of the given statuses, v1 first. */
	private UUID seedContent(VersionStatus... statuses) {
		UUID contentId = UUID.randomUUID();
		this.contentRepository.save(new ContentEntity(contentId, EntityType.DEVIL_FRUIT_TYPE, NOW));
		for (int index = 0; index < statuses.length; index++) {
			var workflow = ContentVersionEntity.builder()
				.contentId(contentId)
				.versionNumber(index + 1)
				.author(UserMapper.toEmbeddable(this.nami))
				.status(statuses[index])
				.createdAt(NOW)
				.updatedAt(NOW)
				.build();
			var version = new DevilFruitTypeVersionEntity(workflow);
			version.setRomaji("Logia " + contentId + " v" + (index + 1));
			version.getTranslations()
				.put("it", new TranslationEmbeddable("Rogia", "Trasforma in un elemento", null, null));
			this.versionRepository.save(version);
		}
		return contentId;
	}

}
