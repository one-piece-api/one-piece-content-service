package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
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
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;
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
import org.hibernate.exception.ConstraintViolationException;
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

import static org.mockito.Mockito.mock;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Creating a Devil Fruit and editing its draft against a real PostgreSQL (Testcontainers)
 * - the same workflow as the Devil Fruit Type, which is covered there - and what is its
 * own: the link to a type, who may be linked (implementation plan of the Devil Fruit, D1,
 * D2, D7), uniqueness among the fruits only, and what the foreign key guarantees whatever
 * the service does.
 * <p>
 * Seeded for every test, all by chopper:
 * <ul>
 * <li>types: Paramecia and Zoan, each online in v1; Logia, a draft and nothing else</li>
 * <li>fruit Gomu Gomu - v1 online, complete in both languages, of the Paramecia</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitDraftIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private DevilFruitVersionRepository versionRepository;

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

	private DevilFruitService service;

	private UUID paramecia;

	private UUID zoan;

	private UUID logia;

	private UUID gomuGomu;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitValidator(this.versionRepository, this.languageRepository, this.typeRepository);
		this.service = new DevilFruitService(
				new DevilFruitDefinition(this.versionRepository, validator,
						new DevilFruitRules(this.contentRepository, this.typeRepository)),
				this.contentVersionRepository, this.contentRepository, auditLogService, clock,
				mock(ContentImages.class));

		this.paramecia = typeContent("Paramecia", PUBLISHED);
		this.zoan = typeContent("Zoan", PUBLISHED);
		this.logia = typeContent("Logia", DRAFT);
		this.gomuGomu = content(EntityType.DEVIL_FRUIT);
		fruitVersion(this.gomuGomu, 1, PUBLISHED, complete("Gomu Gomu", this.paramecia));
		stored();
	}

	@Test
	void aNewFruitIsBornWithADraftSayingWhatWasWrittenItsTypeIncluded() {
		var written = new DevilFruit("  Mera Mera ", this.paramecia,
				Map.of("it", new DevilFruitTranslation(" Fuoco ", "  ", null, null)));

		var content = this.service.create(EDITOR, this.nami, written);
		stored();

		var version = this.service.getVersion(EDITOR, this.nami, content.id(), 1).version();
		assertThat(version.status()).isEqualTo(DRAFT);
		assertThat(version.author()).isEqualTo(this.nami);
		assertThat(version.body()).isEqualTo(new DevilFruit("Mera Mera", this.paramecia,
				Map.of("it", new DevilFruitTranslation("Fuoco", null, null, null))));
	}

	@Test
	void aDraftMayHaveNoTypeYet() {
		var content = this.service.create(EDITOR, this.nami, new DevilFruit("Mera Mera", null, Map.of()));
		stored();

		assertThat(this.service.getVersion(EDITOR, this.nami, content.id(), 1).version().body().typeContentId())
			.isNull();
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "PUBLISHED", "ARCHIVED", "RETIRED", "SUPERSEDED" })
	void aTypeWithAnApprovedVersionCanBeLinkedWhateverBecameOfIt(VersionStatus status) {
		UUID type = typeContent("Kodai", status);
		stored();

		var content = this.service.create(EDITOR, this.nami, new DevilFruit("Mera Mera", type, Map.of()));

		assertThat(content.versions()).hasSize(1);
	}

	@ParameterizedTest
	@EnumSource(names = { "DRAFT", "IN_REVIEW", "REJECTED" })
	void aTypeWithNothingApprovedCannotBeLinked(VersionStatus status) {
		UUID type = typeContent("Kodai", status);
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, this.nami, new DevilFruit("Mera Mera", type, Map.of())));

		assertThat(fieldsOf(refused)).containsExactly("type");
	}

	@Test
	void anIdThatIsNoTypeIsRefusedLikeATypeNotToBeLinked() {
		// Nothing there, and a content that exists but is a fruit: told alike.
		for (UUID notAType : List.of(UUID.randomUUID(), this.gomuGomu)) {
			var refused = catchThrowableOfType(ValueInvalidException.class,
					() -> this.service.create(EDITOR, this.nami, new DevilFruit("Mera Mera", notAType, Map.of())));

			assertThat(fieldsOf(refused)).containsExactly("type");
		}
	}

	@Test
	void theTypeIsCheckedAgainWhenTheDraftIsEdited() {
		var draft = this.service.create(EDITOR, this.nami, new DevilFruit("Mera Mera", this.paramecia, Map.of()));
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.service.edit(EDITOR, this.nami,
				draft.id(), 1, new DevilFruit("Mera Mera", this.logia, Map.of())));

		assertThat(fieldsOf(refused)).containsExactly("type");
	}

	@Test
	void aDraftWithoutATypeIsNotSentToReview() {
		UUID draft = content(EntityType.DEVIL_FRUIT);
		fruitVersion(draft, 1, DRAFT, complete("Mera Mera", null));
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, this.chopper, draft, 1));

		assertThat(fieldsOf(refused)).containsExactly("type");
	}

	@Test
	void aCompleteDraftWithATypeIsSentToReview() {
		UUID draft = content(EntityType.DEVIL_FRUIT);
		fruitVersion(draft, 1, DRAFT, complete("Mera Mera", this.paramecia));
		stored();

		var submitted = this.service.submit(EDITOR, this.chopper, draft, 1);

		assertThat(submitted.version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void aRomajiAFruitHasIsRefusedButTheOneOfATypeIsFree() {
		// Each entity has its own values and its own addresses: "Paramecia" is a type.
		this.service.create(EDITOR, this.nami, new DevilFruit("Paramecia", this.paramecia, Map.of()));

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.create(EDITOR, this.nami, new DevilFruit(" gomu gomu ", this.zoan, Map.of())));

		assertThat(fieldsOf(refused)).containsExactly("romaji");
	}

	@Test
	void aNameAFruitHasInALanguageIsRefused() {
		var written = new DevilFruit("Mera Mera", this.paramecia,
				Map.of("en", new DevilFruitTranslation("GOMU GOMU NO MI", null, null, null)));

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.create(EDITOR, this.nami, written));

		assertThat(fieldsOf(refused)).containsExactly("translations[en].name");
	}

	@Test
	void aNewVersionSayingTheSameIsRefusedUntilItsTypeChanges() {
		this.service.openNewVersion(EDITOR, this.nami, this.gomuGomu, 1);
		stored();

		assertThatThrownBy(() -> this.service.submit(EDITOR, this.nami, this.gomuGomu, 2)).isInstanceOfSatisfying(
				VersionIdenticalException.class,
				refused -> assertThat(refused.getDetails()).containsEntry("identicalTo", 1));

		this.service.edit(EDITOR, this.nami, this.gomuGomu, 2, complete("Gomu Gomu", this.zoan));
		stored();
		assertThat(this.service.submit(EDITOR, this.nami, this.gomuGomu, 2).version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void theDatabaseRefusesAFruitPointingToAFruit() {
		UUID draft = content(EntityType.DEVIL_FRUIT);
		fruitVersion(draft, 1, DRAFT, complete("Mera Mera", this.gomuGomu));

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_devil_fruit_version_type");
	}

	@Test
	void theDatabaseKeepsATypeWithFruitsFromBeingDeleted() {
		var delete = this.entityManager.getEntityManager().createNativeQuery("delete from content where id = ?");
		delete.setParameter(1, this.paramecia);

		assertThatThrownBy(delete::executeUpdate).hasMessageContaining("fk_devil_fruit_version_type");
	}

	@Test
	void aLanguageAFruitSaysSomethingInIsInUse() {
		assertThat(this.versionRepository.existsByLanguage("it")).isTrue();
		assertThat(this.versionRepository.existsByLanguage("fr")).isFalse();
	}

	private static List<String> fieldsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

	/** Complete in both languages of the catalog, so only the type can be missing. */
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
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, 1, status));
		version.setRomaji(romaji);
		version.getTranslations().put("it", new TranslationEmbeddable(romaji, "Descrizione", "Pro", "Contro"));
		this.typeRepository.save(version);
		return contentId;
	}

	private void fruitVersion(UUID contentId, int number, VersionStatus status, DevilFruit body) {
		var version = new DevilFruitVersionEntity(workflow(contentId, number, status));
		DevilFruitVersionMapper.rewrite(version, body, EARLIER);
		this.versionRepository.save(version);
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
