package dev.onepieceapi.contentservice.service;

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
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Two editors opening a new version of the same content at the same instant (UF-CNT-08),
 * each in their own transaction against a real PostgreSQL (Testcontainers). Both are held
 * right after checking that the content has no open version, so both pass it: the
 * database must then let only one of them in, and the other must be refused as a
 * conflict, not fail with a server error. Not run inside a test transaction: each editor
 * commits, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DevilFruitTypeNewVersionRaceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final long WAIT_SECONDS = 10;

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

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
	private PlatformTransactionManager transactionManager;

	@Test
	void ofTwoEditorsOpeningAtOnceOneGetsTheNewVersionAndTheOtherAConflict() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID logia = transaction.execute(status -> seedOnlineContent());
		DevilFruitTypeService service = serviceWithBothEditorsHeldAfterTheCheck(logia);

		List<Callable<VersionAccess<DevilFruitType>>> editors = List.of(
				() -> transaction.execute(status -> service.openNewVersion(EDITOR, this.nami, logia, 1)),
				() -> transaction.execute(status -> service.openNewVersion(EDITOR, this.chopper, logia, 1)));
		List<Object> outcomes = new ArrayList<>();
		try (var executor = Executors.newFixedThreadPool(editors.size())) {
			for (Future<VersionAccess<DevilFruitType>> future : executor.invokeAll(editors)) {
				outcomes.add(outcomeOf(future));
			}
		}

		assertThat(outcomes).filteredOn(VersionAccess.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(VersionActionConflictException.class::isInstance).hasSize(1);
		Integer latest = transaction.execute(status -> this.contentVersionRepository.findLatestNumber(logia));
		assertThat(latest).isEqualTo(2);
	}

	/**
	 * The service, with its view of the versions holding each caller right after it
	 * checked for an open version, until both have checked.
	 */
	private DevilFruitTypeService serviceWithBothEditorsHeldAfterTheCheck(UUID contentId) {
		var bothChecked = new CyclicBarrier(2);
		ContentVersionRepository heldAfterCheck = mock(ContentVersionRepository.class,
				delegatesTo(this.contentVersionRepository));
		doAnswer(invocation -> {
			boolean open = this.contentVersionRepository.hasOpenVersion(contentId);
			bothChecked.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return open;
		}).when(heldAfterCheck).hasOpenVersion(contentId);
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		return new DevilFruitTypeService(this.versionRepository, heldAfterCheck, this.contentRepository, validator,
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

	/** What an editor got: the new version, or why it was refused. */
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

	/** Logia, by nami: v1 online, nothing open. */
	private UUID seedOnlineContent() {
		UUID contentId = UUID.randomUUID();
		this.contentRepository.save(new ContentEntity(contentId, EntityType.DEVIL_FRUIT_TYPE, NOW));
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(this.nami))
			.status(VersionStatus.PUBLISHED)
			.createdAt(NOW)
			.updatedAt(NOW)
			.build();
		var version = new DevilFruitTypeVersionEntity(workflow);
		version.setRomaji("Logia");
		version.getTranslations().put("it", new TranslationEmbeddable("Rogia", "Trasforma in un elemento"));
		this.versionRepository.save(version);
		return contentId;
	}

}
