package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * A fruit going online and its type being retired at the same instant, each in its own
 * transaction against a real PostgreSQL (Testcontainers) - where, without the lock on the
 * type's content, both would read a state the other is about to change and both would
 * pass, leaving a fruit online with a type that is not (implementation plan of the Devil
 * Fruit, D2). The side that comes first is held right after it read what its rule needs,
 * and the other is started: it must wait for the first to finish, then find what the
 * first wrote and be refused. Not run inside a test transaction: each caller commits, as
 * in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DevilFruitRulesRaceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	private static final long WAIT_SECONDS = 10;

	/** Long enough for a caller that is not waiting on a lock to have finished. */
	private static final long SETTLE_MILLIS = 700;

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

	@Autowired
	private DevilFruitVersionRepository fruitRepository;

	@Autowired
	private DevilFruitTypeVersionRepository typeRepository;

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
	void aFruitPublishedWhileItsTypeIsBeingRetiredWaitsAndIsRefusedOnceTheTypeIsOffline() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID type = transaction.execute(status -> typeContent(PUBLISHED));
		UUID fruit = transaction.execute(status -> fruitContent(type, READY_TO_PUBLISH));
		var retireCountedFruits = new CountDownLatch(1);
		var letRetireGo = new CountDownLatch(1);
		DevilFruitVersionRepository holdingCount = mock(DevilFruitVersionRepository.class,
				delegatesTo(this.fruitRepository));
		doAnswer(invocation -> {
			long count = this.fruitRepository.countOnlineLinkedTo(type);
			retireCountedFruits.countDown();
			letRetireGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return count;
		}).when(holdingCount).countOnlineLinkedTo(type);
		var types = typeService(holdingCount, this.typeRepository);
		var fruits = fruitService(this.typeRepository);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> retire = executor
				.submit(() -> transaction.execute(status -> types.retire(RETIRER, this.vivi, type, 1)));
			assertThat(retireCountedFruits.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> publish = executor
				.submit(() -> transaction.execute(status -> fruits.publish(PUBLISHER, this.luffy, fruit, 1)));

			Thread.sleep(SETTLE_MILLIS);
			assertThat(publish.isDone()).as("the publication waits for the retirement").isFalse();
			letRetireGo.countDown();

			assertThat(retire.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
			assertThatThrownBy(() -> publish.get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(VersionActionBlockedException.class)
				.cause()
				.satisfies(refused -> assertThat(((VersionActionBlockedException) refused).getDetails())
					.containsEntry("reason", BlockReason.TYPE_NOT_ONLINE));
		}

		Boolean typeOnline = transaction.execute(status -> this.typeRepository.findOnline(type).isPresent());
		Boolean fruitOnline = transaction.execute(status -> this.fruitRepository.findOnline(fruit).isPresent());
		assertThat(typeOnline).isFalse();
		assertThat(fruitOnline).isFalse();
	}

	@Test
	void aTypeRetiredWhileOneOfItsFruitsIsGoingOnlineWaitsAndIsRefusedOnceTheFruitIsOnline() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID type = transaction.execute(status -> typeContent(PUBLISHED));
		UUID fruit = transaction.execute(status -> fruitContent(type, READY_TO_PUBLISH));
		var publishFoundTypeOnline = new CountDownLatch(1);
		var letPublishGo = new CountDownLatch(1);
		DevilFruitTypeVersionRepository holdingRead = mock(DevilFruitTypeVersionRepository.class,
				delegatesTo(this.typeRepository));
		doAnswer(invocation -> {
			boolean online = this.typeRepository.existsWithStatus(type, Set.of(PUBLISHED));
			publishFoundTypeOnline.countDown();
			letPublishGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return online;
		}).when(holdingRead).existsWithStatus(eq(type), any());
		var fruits = fruitService(holdingRead);
		var types = typeService(this.fruitRepository, this.typeRepository);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> publish = executor
				.submit(() -> transaction.execute(status -> fruits.publish(PUBLISHER, this.luffy, fruit, 1)));
			assertThat(publishFoundTypeOnline.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> retire = executor
				.submit(() -> transaction.execute(status -> types.retire(RETIRER, this.vivi, type, 1)));

			Thread.sleep(SETTLE_MILLIS);
			assertThat(retire.isDone()).as("the retirement waits for the publication").isFalse();
			letPublishGo.countDown();

			assertThat(publish.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
			assertThatThrownBy(() -> retire.get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(VersionActionBlockedException.class)
				.cause()
				.satisfies(refused -> assertThat(((VersionActionBlockedException) refused).getDetails())
					.containsEntry("reason", BlockReason.ONLINE_FRUITS_LINKED));
		}

		Boolean typeOnline = transaction.execute(status -> this.typeRepository.findOnline(type).isPresent());
		Boolean fruitOnline = transaction.execute(status -> this.fruitRepository.findOnline(fruit).isPresent());
		assertThat(typeOnline).isTrue();
		assertThat(fruitOnline).isTrue();
	}

	private DevilFruitTypeService typeService(DevilFruitVersionRepository fruits,
			DevilFruitTypeVersionRepository types) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new DevilFruitTypeService(types, this.contentVersionRepository, this.contentRepository,
				new DevilFruitTypeValidator(this.typeRepository, this.languageRepository),
				new DevilFruitTypeRules(this.contentRepository, fruits, new RulesProperties(5)),
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

	private DevilFruitService fruitService(DevilFruitTypeVersionRepository types) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new DevilFruitService(this.fruitRepository, this.contentVersionRepository, this.contentRepository,
				new DevilFruitValidator(this.fruitRepository, this.languageRepository, types),
				new DevilFruitRules(this.contentRepository, types), new AuditLogService(this.auditLogRepository, clock),
				clock);
	}

	/**
	 * A type with one version in this status; a name of its own, so runs never collide.
	 */
	private UUID typeContent(VersionStatus status) {
		UUID contentId = this.contentRepository
			.save(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT_TYPE, NOW))
			.getId();
		String romaji = "Type " + contentId;
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, status));
		version.setRomaji(romaji);
		version.getTranslations().put("it", new TranslationEmbeddable(romaji, "Descrizione", "Pro", "Contro"));
		this.typeRepository.save(version);
		return contentId;
	}

	private UUID fruitContent(UUID type, VersionStatus status) {
		UUID contentId = this.contentRepository.save(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT, NOW))
			.getId();
		String romaji = "Fruit " + contentId;
		var version = new DevilFruitVersionEntity(workflow(contentId, status));
		DevilFruitVersionMapper.rewrite(version, new DevilFruit(romaji, type,
				Map.of("it", new DevilFruitTranslation(romaji, "Descrizione", "Pro", "Contro"))), NOW);
		this.fruitRepository.save(version);
		return contentId;
	}

	private ContentVersionEntity workflow(UUID contentId, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(this.vivi))
			.status(status)
			.createdAt(NOW)
			.updatedAt(NOW)
			.build();
	}

}
