package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
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

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What a Devil Fruit and its type owe each other (implementation plan of the Devil Fruit,
 * D2, D3) against a real PostgreSQL (Testcontainers), one test per row of the plan's
 * table: a fruit goes online only while its type is online; a type is not retired while
 * fruits are online with it; nothing else is asked of the other side. Whoever tries
 * anyway is refused with the reason, even an administrator, and the reason is listed with
 * the version before anyone tries. The two sides running at the same instant are in
 * {@link DevilFruitRulesRaceIntegrationTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitRulesIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

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
	private TestEntityManager entityManager;

	private DevilFruitService fruits;

	private DevilFruitTypeService types;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		this.fruits = new DevilFruitService(this.fruitRepository, this.contentVersionRepository, this.contentRepository,
				new DevilFruitValidator(this.fruitRepository, this.languageRepository, this.typeRepository),
				new DevilFruitRules(this.contentRepository, this.typeRepository), auditLogService, clock);
		this.types = new DevilFruitTypeService(this.typeRepository, this.contentVersionRepository,
				this.contentRepository, new DevilFruitTypeValidator(this.typeRepository, this.languageRepository),
				new DevilFruitTypeRules(this.contentRepository, this.fruitRepository, new RulesProperties(5)),
				auditLogService, clock);
	}

	@Test
	void aFruitOfATypeNoLongerOnlineCanStillBeSubmittedReviewedAndSetAside() {
		UUID type = typeContent("Logia", RETIRED);
		UUID fruit = fruitContent("Mera Mera", type, DRAFT);
		stored();

		assertThat(this.fruits.submit(EDITOR, this.chopper, fruit, 1).version().status()).isEqualTo(IN_REVIEW);
		this.fruits.claim(REVIEWER, this.zoro, fruit, 1);
		assertThat(this.fruits.approve(REVIEWER, this.zoro, fruit, 1).version().status()).isEqualTo(READY_TO_PUBLISH);
		assertThat(this.fruits.archive(PUBLISHER, this.vivi, fruit, 1).version().status()).isEqualTo(ARCHIVED);
		assertThat(this.fruits.recover(PUBLISHER, this.vivi, fruit, 1).version().status()).isEqualTo(READY_TO_PUBLISH);
	}

	@Test
	void aFruitGoesOnlineWhileItsTypeIs() {
		UUID type = typeContent("Paramecia", PUBLISHED);
		UUID fruit = fruitContent("Gomu Gomu", type, READY_TO_PUBLISH);
		stored();

		assertThat(this.fruits.publish(PUBLISHER, this.vivi, fruit, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "ARCHIVED", "RETIRED" })
	void aFruitDoesNotGoOnlineWhileItsTypeIsNot(VersionStatus typeStatus) {
		UUID type = typeContent("Paramecia", typeStatus);
		UUID fruit = fruitContent("Gomu Gomu", type, READY_TO_PUBLISH);
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(PUBLISHER, this.vivi, fruit, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.TYPE_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("typeId", type, "typeRomaji", "Paramecia"));
		stored();
		assertThat(statusOfFruit(fruit, 1)).isEqualTo(READY_TO_PUBLISH);
	}

	@Test
	void aFruitTakenOfflineIsRestoredWhileItsTypeIsOnlineAndNotBefore() {
		UUID offline = typeContent("Logia", RETIRED);
		UUID online = typeContent("Zoan", PUBLISHED);
		UUID early = fruitContent("Mera Mera", offline, RETIRED);
		UUID fine = fruitContent("Hito Hito", online, RETIRED);
		stored();

		assertThatThrownBy(() -> this.fruits.restore(PUBLISHER, this.vivi, early, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThat(this.fruits.restore(PUBLISHER, this.vivi, fine, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void theVersionSaysBeforeAnyoneTriesWhichOfItsActionsAreBlocked() {
		UUID offline = typeContent("Logia", RETIRED);
		UUID online = typeContent("Zoan", PUBLISHED);
		UUID blocked = fruitContent("Mera Mera", offline, READY_TO_PUBLISH);
		UUID free = fruitContent("Hito Hito", online, READY_TO_PUBLISH);
		stored();

		var withReason = this.fruits.getVersion(PUBLISHER, this.vivi, blocked, 1);
		var without = this.fruits.getVersion(PUBLISHER, this.vivi, free, 1);

		assertThat(withReason.allowedActions()).contains(VersionAction.PUBLISH, VersionAction.ARCHIVE);
		assertThat(withReason.blockedActions()).containsExactly(new BlockedAction(VersionAction.PUBLISH,
				BlockReason.TYPE_NOT_ONLINE, Map.of("typeId", offline, "typeRomaji", "Logia")));
		assertThat(without.blockedActions()).isEmpty();
	}

	@Test
	void aTypeIsNotRetiredWhileFruitsAreOnlineWithItAndTheRefusalNamesSome() {
		UUID type = typeContent("Paramecia", PUBLISHED);
		for (int number = 1; number <= 6; number++) {
			fruitContent("Fruit " + number, type, PUBLISHED);
		}
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.retire(RETIRER, this.vivi, type, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_FRUITS_LINKED);
		@SuppressWarnings("unchecked")
		var detail = (Map<String, Object>) refused.getDetails().get("detail");
		assertThat(detail).containsEntry("count", 6L);
		@SuppressWarnings("unchecked")
		var named = (List<Map<String, Object>>) detail.get("fruits");
		assertThat(named).extracting(fruit -> fruit.get("romaji"))
			.containsExactly("Fruit 1", "Fruit 2", "Fruit 3", "Fruit 4", "Fruit 5");
		stored();
		assertThat(statusOfType(type, 1)).isEqualTo(PUBLISHED);
	}

	@Test
	void theVersionOfATypeListsTheRetirementItCannotDo() {
		UUID type = typeContent("Paramecia", PUBLISHED);
		fruitContent("Gomu Gomu", type, PUBLISHED);
		UUID alone = typeContent("Zoan", PUBLISHED);
		stored();

		var withFruit = this.types.getVersion(RETIRER, this.vivi, type, 1);
		var withoutFruit = this.types.getVersion(RETIRER, this.vivi, alone, 1);

		assertThat(withFruit.allowedActions()).contains(VersionAction.RETIRE);
		assertThat(withFruit.blockedActions()).extracting(BlockedAction::action, BlockedAction::reason)
			.containsExactly(tuple(VersionAction.RETIRE, BlockReason.ONLINE_FRUITS_LINKED));
		assertThat(withoutFruit.blockedActions()).isEmpty();
	}

	@Test
	void fruitsNotOnlineWithTheTypeDoNotKeepItOnline() {
		UUID type = typeContent("Paramecia", PUBLISHED);
		UUID other = typeContent("Zoan", PUBLISHED);
		for (VersionStatus status : List.of(DRAFT, READY_TO_PUBLISH, ARCHIVED, RETIRED, SUPERSEDED)) {
			fruitContent("Fruit " + status, type, status);
		}
		fruitContent("Elsewhere", other, PUBLISHED);
		stored();

		assertThat(this.types.retire(RETIRER, this.vivi, type, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void onlyTheOnlineVersionOfAFruitSaysWhichTypeItKeepsOnline() {
		UUID type = typeContent("Paramecia", PUBLISHED);
		UUID other = typeContent("Zoan", PUBLISHED);
		UUID fruit = fruitContent("Gomu Gomu", type, SUPERSEDED);
		fruitVersion(fruit, 2, PUBLISHED, complete("Gomu Gomu", other));
		stored();

		assertThat(this.types.retire(RETIRER, this.vivi, type, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void aNewOnlineVersionOfTheTypeChangesNothingForItsFruits() {
		UUID type = typeContent("Paramecia", PUBLISHED);
		typeVersion(type, 2, READY_TO_PUBLISH, "Paramecia");
		UUID fruit = fruitContent("Gomu Gomu", type, PUBLISHED);
		stored();

		this.types.publish(PUBLISHER, this.vivi, type, 2);
		stored();

		assertThat(statusOfFruit(fruit, 1)).isEqualTo(PUBLISHED);
		assertThatThrownBy(() -> this.types.retire(RETIRER, this.vivi, type, 2))
			.isInstanceOf(VersionActionBlockedException.class);
	}

	@Test
	void anAdministratorIsRefusedToo() {
		UUID offline = typeContent("Logia", RETIRED);
		UUID early = fruitContent("Mera Mera", offline, READY_TO_PUBLISH);
		UUID online = typeContent("Paramecia", PUBLISHED);
		fruitContent("Gomu Gomu", online, PUBLISHED);
		stored();

		assertThatThrownBy(() -> this.fruits.publish(ADMIN, this.vivi, early, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThatThrownBy(() -> this.types.retire(ADMIN, this.vivi, online, 1))
			.isInstanceOf(VersionActionBlockedException.class);
	}

	private VersionStatus statusOfFruit(UUID contentId, int number) {
		return this.fruits.getVersion(ADMIN, this.vivi, contentId, number).version().status();
	}

	private VersionStatus statusOfType(UUID contentId, int number) {
		return this.types.getVersion(ADMIN, this.vivi, contentId, number).version().status();
	}

	/** Complete in both languages of the catalog. */
	private static DevilFruit complete(String romaji, UUID type) {
		return new DevilFruit(romaji, type,
				Map.of("it", new DevilFruitTranslation(romaji + " it", "Descrizione", "Pro", "Contro"), "en",
						new DevilFruitTranslation(romaji + " no Mi", "Description", "Pros", "Cons")));
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID content(EntityType entityType) {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	/** A type with a single version in this status. */
	private UUID typeContent(String romaji, VersionStatus status) {
		UUID contentId = content(EntityType.DEVIL_FRUIT_TYPE);
		typeVersion(contentId, 1, status, romaji);
		return contentId;
	}

	private void typeVersion(UUID contentId, int number, VersionStatus status, String romaji) {
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, number, status));
		version.setRomaji(romaji);
		version.getTranslations().put("it", new TranslationEmbeddable(romaji, "Descrizione", "Pro", "Contro"));
		this.typeRepository.save(version);
	}

	/** A fruit of this type with a single version in this status. */
	private UUID fruitContent(String romaji, UUID type, VersionStatus status) {
		UUID contentId = content(EntityType.DEVIL_FRUIT);
		fruitVersion(contentId, 1, status, complete(romaji, type));
		return contentId;
	}

	private void fruitVersion(UUID contentId, int number, VersionStatus status, DevilFruit body) {
		var version = new DevilFruitVersionEntity(workflow(contentId, number, status));
		DevilFruitVersionMapper.rewrite(version, body, EARLIER);
		this.fruitRepository.save(version);
	}

	private ContentVersionEntity workflow(UUID contentId, int number, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(this.chopper))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
	}

}
