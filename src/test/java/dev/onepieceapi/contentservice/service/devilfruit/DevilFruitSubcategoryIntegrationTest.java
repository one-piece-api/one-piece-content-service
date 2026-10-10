package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruit.SubcategoryReference;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
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
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeLinks;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeRules;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeService;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The subcategories of a Devil Fruit Type and the fruits naming them (implementation plan
 * of the subcategories, S1-S7) against a real PostgreSQL (Testcontainers): the type's
 * drafts keep their ids, the server gives new ones, a client can neither invent nor
 * repeat one; a fruit names only a subcategory of its type as last approved, goes online
 * only while the type's online version has it, and keeps a type version leaving it out
 * from going online - an administrator included.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitSubcategoryIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private static final UUID ANCIENT = UUID.randomUUID();

	private static final UUID MYTHICAL = UUID.randomUUID();

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
		this.fruits = new DevilFruitService(
				new DevilFruitDefinition(this.fruitRepository,
						new DevilFruitValidator(this.fruitRepository, this.languageRepository, this.typeRepository),
						new DevilFruitRules(this.contentRepository, this.typeRepository)),
				this.contentVersionRepository, this.contentRepository, auditLogService, clock,
				mock(ContentImages.class));
		this.types = new DevilFruitTypeService(this.typeRepository, this.contentVersionRepository,
				this.contentRepository, new DevilFruitTypeValidator(this.typeRepository, this.languageRepository),
				new DevilFruitTypeRules(this.contentRepository, this.fruitRepository, new RulesProperties(5)),
				auditLogService, clock);
	}

	@Test
	void aNewTypeGivesEachOfItsSubcategoriesAnIdAndKeepsTheirOrder() {
		var created = this.types.create(EDITOR, this.chopper,
				zoan(subcategory(null, "Antico"), subcategory(null, "Mitologico")));
		stored();

		var subcategories = this.types.get(ADMIN, this.vivi, created.id())
			.versions()
			.getFirst()
			.version()
			.body()
			.subcategories();

		assertThat(subcategories).extracting(subcategory -> subcategory.names().get("it"))
			.containsExactly("Antico", "Mitologico");
		assertThat(subcategories).allSatisfy(subcategory -> assertThat(subcategory.id()).isNotNull());
	}

	@Test
	void aNewTypeIsNotSavedWithAnIdTheClientMadeUp() {
		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.types.create(EDITOR, this.chopper, zoan(subcategory(ANCIENT, "Antico"))));

		assertThat(violations(refused)).extracting(FieldViolation::field).containsExactly("subcategories[0].id");
	}

	@Test
	void aSavedDraftKeepsTheIdsItSentRenamedReorderedAndGivesOneToANewSubcategory() {
		UUID type = typeContent(DRAFT, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitologico")));
		stored();

		var edited = this.types.edit(EDITOR, this.chopper, type, 1, zoan(subcategory(MYTHICAL, "Mitico"),
				subcategory(null, "Artificiale"), subcategory(ANCIENT, "Antico")));
		stored();

		var subcategories = this.types.getVersion(ADMIN, this.vivi, type, 1).version().body().subcategories();
		assertThat(edited.version().body().subcategories()).isEqualTo(subcategories);
		assertThat(subcategories).extracting(DevilFruitTypeSubcategory::id).startsWith(MYTHICAL).endsWith(ANCIENT);
		assertThat(subcategories.get(1).id()).isNotIn(ANCIENT, MYTHICAL).isNotNull();
		assertThat(subcategories).extracting(subcategory -> subcategory.names().get("it"))
			.containsExactly("Mitico", "Artificiale", "Antico");
	}

	@Test
	void aSubcategoryRemovedFromADraftCannotComeBackWithItsIdAndIdsAreNotRepeated() {
		UUID type = typeContent(DRAFT, zoan(subcategory(ANCIENT, "Antico")));
		stored();

		var unknown = catchThrowableOfType(ValueInvalidException.class, () -> this.types.edit(EDITOR, this.chopper,
				type, 1, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitologico"))));
		var repeated = catchThrowableOfType(ValueInvalidException.class, () -> this.types.edit(EDITOR, this.chopper,
				type, 1, zoan(subcategory(ANCIENT, "Antico"), subcategory(ANCIENT, "Antichissimo"))));

		assertThat(violations(unknown)).extracting(FieldViolation::field).containsExactly("subcategories[1].id");
		assertThat(violations(repeated)).extracting(FieldViolation::field).containsExactly("subcategories[1].id");
	}

	@Test
	void twoSubcategoriesOfATypeDoNotShareANameInALanguage() {
		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.types.create(EDITOR, this.chopper,
				zoan(subcategory(null, "Antico"), subcategory(null, " antico "))));

		assertThat(violations(refused)).extracting(FieldViolation::field)
			.containsExactly("subcategories[1].translations[it].name");
	}

	@Test
	void aTypeWithASubcategoryMissingItsDescriptionIsNotSubmitted() {
		var subcategory = new DevilFruitTypeSubcategory(ANCIENT,
				Map.of("it", new DevilFruitTypeSubcategoryTranslation("Antico", null), "en",
						new DevilFruitTypeSubcategoryTranslation("Ancient", "Extinct animals")));
		UUID type = typeContent(DRAFT, zoan(subcategory));
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.types.submit(EDITOR, this.chopper, type, 1));

		assertThat(violations(refused)).extracting(FieldViolation::field)
			.containsExactly("subcategories[0].translations[it].description");
	}

	@Test
	void aNewVersionOfATypeKeepsTheIdsOfItsSubcategories() {
		UUID type = typeContent(PUBLISHED, zoan(subcategory(ANCIENT, "Antico")));
		stored();

		var draft = this.types.openNewVersion(EDITOR, this.chopper, type, 1);

		assertThat(draft.version().body().subcategoryIds()).containsExactly(ANCIENT);
	}

	@Test
	void aLanguageASubcategoryIsWrittenInIsInUse() {
		UUID type = typeContent(DRAFT,
				new DevilFruitType("Dobutsu", Map.of(), List.of(new DevilFruitTypeSubcategory(ANCIENT,
						Map.of("en", new DevilFruitTypeSubcategoryTranslation("Ancient", null))))));
		stored();

		assertThat(this.typeRepository.findOnline(type)).isEmpty();
		assertThat(this.typeRepository.existsByLanguage("en")).isTrue();
		assertThat(this.typeRepository.existsByLanguage("it")).isFalse();
	}

	@Test
	void aFruitNamesASubcategoryOfItsTypeAsLastApproved() {
		UUID type = typeContent(PUBLISHED, zoan(subcategory(ANCIENT, "Antico")));
		typeVersion(type, 2, READY_TO_PUBLISH, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		stored();

		var created = this.fruits.create(EDITOR, this.chopper, fruit("Uo Uo", type, MYTHICAL));

		assertThat(created.versions().getFirst().version().body().subcategoryId()).isEqualTo(MYTHICAL);
	}

	@Test
	void aFruitIsNotSavedWithASubcategoryOfAnotherTypeNoneOrOneRemoved() {
		UUID zoan = typeContent(PUBLISHED, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		typeVersion(zoan, 2, READY_TO_PUBLISH, zoan(subcategory(ANCIENT, "Antico")));
		UUID logia = typeContent(PUBLISHED, new DevilFruitType("Logia", Map.of()));
		stored();

		for (DevilFruit refusedFruit : List.of(fruit("Uo Uo", logia, ANCIENT), fruit("Uo Uo", null, ANCIENT),
				fruit("Uo Uo", zoan, MYTHICAL), fruit("Uo Uo", zoan, UUID.randomUUID()))) {
			var refused = catchThrowableOfType(ValueInvalidException.class,
					() -> this.fruits.create(EDITOR, this.chopper, refusedFruit));
			assertThat(violations(refused)).extracting(FieldViolation::field).containsExactly("subcategory");
		}
	}

	@Test
	void aFruitGoesOnlineOnlyWhileTheOnlineVersionOfItsTypeHasItsSubcategory() {
		UUID type = typeContent(PUBLISHED, zoan(subcategory(ANCIENT, "Antico")));
		typeVersion(type, 2, READY_TO_PUBLISH, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		UUID early = fruitContent(READY_TO_PUBLISH, fruit("Uo Uo", type, MYTHICAL));
		UUID fine = fruitContent(READY_TO_PUBLISH, fruit("Neko Neko", type, ANCIENT));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(PUBLISHER, this.vivi, early, 1));
		var blocked = this.fruits.getVersion(PUBLISHER, this.vivi, early, 1).blockedActions();

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.SUBCATEGORY_NOT_ONLINE);
		assertThat(blocked).containsExactly(
				new BlockedAction(VersionAction.PUBLISH, BlockReason.SUBCATEGORY_NOT_ONLINE, Map.of("typeId", type,
						"subcategoryId", MYTHICAL, "subcategoryNames", Map.of("it", "Mitico", "en", "Mitico (en)"))));
		assertThat(this.fruits.publish(PUBLISHER, this.vivi, fine, 1).version().status()).isEqualTo(PUBLISHED);
		this.types.publish(PUBLISHER, this.vivi, type, 2);
		assertThat(this.fruits.publish(PUBLISHER, this.vivi, early, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aTypeVersionLeavingOutASubcategoryOnlineFruitsUseDoesNotGoOnlineAndNamesThem() {
		UUID type = typeContent(PUBLISHED, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		typeVersion(type, 2, READY_TO_PUBLISH, zoan(subcategory(ANCIENT, "Antico")));
		UUID uo = fruitContent(PUBLISHED, fruit("Uo Uo", type, MYTHICAL));
		fruitContent(PUBLISHED, fruit("Neko Neko", type, ANCIENT));
		fruitContent(PUBLISHED, fruit("Hito Hito", type, null));
		fruitContent(READY_TO_PUBLISH, fruit("Tori Tori", type, MYTHICAL));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.publish(PUBLISHER, this.vivi, type, 2));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.SUBCATEGORY_IN_USE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(
				Map.of("count", 1L, "fruits", List.of(Map.of("id", uo, "romaji", "Uo Uo", "subcategoryId", MYTHICAL))));
		assertThat(this.types.getVersion(PUBLISHER, this.vivi, type, 2).blockedActions())
			.extracting(BlockedAction::reason)
			.containsExactly(BlockReason.SUBCATEGORY_IN_USE);
	}

	@Test
	void aFruitWhoseSubcategoryTheTypeLeftOutIsNotRestoredAndSaysItsNameAsLastApprovedWithIt() {
		UUID type = typeContent(SUPERSEDED, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		typeVersion(type, 2, PUBLISHED, zoan(subcategory(ANCIENT, "Antico")));
		UUID fruit = fruitContent(RETIRED, fruit("Uo Uo", type, MYTHICAL));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.restore(ADMIN, this.vivi, fruit, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.SUBCATEGORY_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("typeId", type, "subcategoryId", MYTHICAL,
				"subcategoryNames", Map.of("it", "Mitico", "en", "Mitico (en)")));
	}

	@Test
	void aFruitWhoseSubcategoryTheTypeLeftOutIsStillShownWithItsNameButItIsNotOffered() {
		UUID type = typeContent(SUPERSEDED, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		typeVersion(type, 2, PUBLISHED, zoan(subcategory(ANCIENT, "Antico")));
		var uo = fruit("Uo Uo", type, MYTHICAL);
		stored();

		var reference = new DevilFruitTypeLinks(this.typeRepository, this.fruitRepository,
				Clock.fixed(NOW, ZoneOffset.UTC))
			.referencesFor(List.of(uo))
			.get(type);

		assertThat(reference.subcategory(MYTHICAL))
			.hasValueSatisfying(mythical -> assertThat(mythical.names()).containsEntry("it", "Mitico")
				.containsEntry("en", "Mitico (en)"));
		assertThat(reference.subcategories()).extracting(SubcategoryReference::id).containsExactly(ANCIENT);
	}

	@Test
	void aRestoredTypeVersionLeavingOutASubcategoryInUseIsRefusedEvenToAnAdministrator() {
		UUID type = typeContent(PUBLISHED, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		typeVersion(type, 2, RETIRED, zoan());
		fruitContent(PUBLISHED, fruit("Uo Uo", type, MYTHICAL));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.restore(ADMIN, this.vivi, type, 2));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.SUBCATEGORY_IN_USE);
		stored();
		assertThat(statusOfType(type, 2)).isEqualTo(RETIRED);
	}

	@Test
	void aTypeVersionKeepingEverySubcategoryInUseGoesOnlineRenamedOrNot() {
		UUID type = typeContent(PUBLISHED, zoan(subcategory(ANCIENT, "Antico"), subcategory(MYTHICAL, "Mitico")));
		typeVersion(type, 2, READY_TO_PUBLISH, zoan(subcategory(MYTHICAL, "Mitologico")));
		fruitContent(PUBLISHED, fruit("Uo Uo", type, MYTHICAL));
		fruitContent(SUPERSEDED, fruit("Neko Neko", type, ANCIENT));
		stored();

		assertThat(this.types.publish(PUBLISHER, this.vivi, type, 2).version().status()).isEqualTo(PUBLISHED);
	}

	private VersionStatus statusOfType(UUID contentId, int number) {
		return this.types.getVersion(ADMIN, this.vivi, contentId, number).version().status();
	}

	@SuppressWarnings("unchecked")
	private static List<FieldViolation> violations(DomainException refused) {
		return (List<FieldViolation>) refused.getDetails().get("errors");
	}

	/** Complete in both languages of the catalog, with these subcategories in order. */
	private static DevilFruitType zoan(DevilFruitTypeSubcategory... subcategories) {
		return new DevilFruitType("Dobutsu",
				Map.of("it", new DevilFruitTypeTranslation("Zoo Zoo", "Descrizione", "Pro", "Contro"), "en",
						new DevilFruitTypeTranslation("Zoan", "Description", "Pros", "Cons")),
				List.of(subcategories));
	}

	/** Complete in both languages; the English name follows the Italian one. */
	private static DevilFruitTypeSubcategory subcategory(UUID id, String name) {
		Map<String, DevilFruitTypeSubcategoryTranslation> translations = new LinkedHashMap<>();
		translations.put("it", new DevilFruitTypeSubcategoryTranslation(name, "Descrizione"));
		translations.put("en", new DevilFruitTypeSubcategoryTranslation(name + " (en)", "Description"));
		return new DevilFruitTypeSubcategory(id, translations);
	}

	private static DevilFruit fruit(String romaji, UUID type, UUID subcategory) {
		return new DevilFruit(romaji, type, subcategory,
				Map.of("it", new DevilFruitTranslation(romaji + " it", "Descrizione", "Pro", "Contro"), "en",
						new DevilFruitTranslation(romaji + " no Mi", "Description", "Pros", "Cons")),
				null);
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
	private UUID typeContent(VersionStatus status, DevilFruitType body) {
		UUID contentId = content(EntityType.DEVIL_FRUIT_TYPE);
		typeVersion(contentId, 1, status, body);
		return contentId;
	}

	private void typeVersion(UUID contentId, int number, VersionStatus status, DevilFruitType body) {
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, number, status));
		DevilFruitTypeVersionMapper.rewrite(version, body, EARLIER);
		this.typeRepository.save(version);
	}

	private UUID fruitContent(VersionStatus status, DevilFruit body) {
		UUID contentId = content(EntityType.DEVIL_FRUIT);
		var version = new DevilFruitVersionEntity(workflow(contentId, 1, status));
		DevilFruitVersionMapper.rewrite(version, body, EARLIER);
		this.fruitRepository.save(version);
		return contentId;
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
