package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitDefinition;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitService;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRules;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruit.TypeReference;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
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
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
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
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What the Devil Fruit Types and the Devil Fruits say of each other, read against a real
 * PostgreSQL (Testcontainers): the list of fruits narrowed to a type, the number of
 * fruits a type shows - which is that list's total, whoever asks - the types a fruit may
 * be linked to and the type a fruit points to as it is today (implementation plan of the
 * Devil Fruit, D1, D4, D5).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeLinksIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> READER = Set.of(Permission.CONTENT_READ);

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_REVIEW);

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

	private DevilFruitTypeLinks links;

	private UUID paramecia;

	private UUID zoan;

	private UUID logia;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.fruits = new DevilFruitService(
				new DevilFruitDefinition(this.fruitRepository,
						new DevilFruitValidator(this.fruitRepository, this.languageRepository, this.typeRepository),
						new DevilFruitRules(this.contentRepository, this.typeRepository)),
				this.contentVersionRepository, this.contentRepository,
				new AuditLogService(this.auditLogRepository, clock), clock, mock(ContentImages.class));
		this.links = new DevilFruitTypeLinks(this.typeRepository, this.fruitRepository, clock);

		this.paramecia = typeContent("Paramecia", "Paramisha", PUBLISHED);
		this.zoan = typeContent("Zoan", "Zoo", READY_TO_PUBLISH);
		this.logia = typeContent("Logia", "Rogia", DRAFT);
	}

	@Test
	void theListIsNarrowedToTheFruitsWhoseVersionShownPointsToTheType() {
		UUID online = fruitContent("Gomu Gomu", this.paramecia, PUBLISHED);
		UUID draftOfZoan = fruitContent("Hito Hito", this.zoan, DRAFT);
		// The newest version points to another type: it is the fruit's row, for the
		// other.
		UUID moved = fruitContent("Mera Mera", this.paramecia, SUPERSEDED);
		fruitVersion(moved, 2, PUBLISHED, complete("Mera Mera", this.zoan));
		stored();

		assertThat(contentIds(EDITOR, this.paramecia)).containsExactly(online);
		assertThat(contentIds(EDITOR, this.zoan)).containsExactlyInAnyOrder(draftOfZoan, moved);
		assertThat(contentIds(EDITOR, this.logia)).isEmpty();
		assertThat(this.fruits
			.list(EDITOR, this.chopper, new ContentFilter(null, null, null, null), PageRequest.of(0, 10), "en")
			.getTotalElements()).isEqualTo(3);
	}

	@Test
	void theCountOfATypeIsTheTotalOfTheListNarrowedToItWhoeverAsks() {
		fruitContent("Gomu Gomu", this.paramecia, PUBLISHED);
		fruitContent("Bara Bara", this.paramecia, ARCHIVED);
		fruitContent("Ito Ito", this.paramecia, DRAFT);
		fruitContent("Doku Doku", this.paramecia, IN_REVIEW);
		UUID moved = fruitContent("Mera Mera", this.paramecia, SUPERSEDED);
		fruitVersion(moved, 2, DRAFT, complete("Mera Mera", this.zoan));
		fruitContent("Hito Hito", this.zoan, READY_TO_PUBLISH);
		stored();

		for (Set<Permission> caller : List.of(READER, EDITOR, REVIEWER, Set.<Permission>of())) {
			Map<UUID, Long> counts = this.links.fruitCounts(caller, List.of(this.paramecia, this.zoan, this.logia));
			for (UUID type : List.of(this.paramecia, this.zoan, this.logia)) {
				assertThat(counts.getOrDefault(type, 0L)).as("%s sees of %s", caller, type)
					.isEqualTo(totalOf(caller, type));
			}
		}
		// One of them, so the numbers are not all zero.
		assertThat(this.links.fruitCounts(READER, List.of(this.paramecia))).containsExactly(entry(this.paramecia, 3L));
	}

	@Test
	void aCountIsAskedForNoTypeOrForACallerSeeingNothingWithoutAQuery() {
		assertThat(this.links.fruitCounts(EDITOR, List.of())).isEmpty();
		assertThat(this.links.fruitCounts(Set.of(), List.of(this.paramecia))).isEmpty();
	}

	@Test
	void onlyTheTypesWithAnApprovedVersionCanBeLinkedByRomajiAndTheirNamesAreThoseApproved() {
		UUID kodai = typeContent("Kodai", "Antico", ARCHIVED);
		typeVersion(this.paramecia, 2, DRAFT, "Paramecia New", "Nuovo nome");
		typeContent("Hidden", "Nascosto", IN_REVIEW);
		stored();

		var page = this.links.linkable(null, 0, 10);

		assertThat(page.getContent()).extracting(TypeReference::romaji).containsExactly("Kodai", "Paramecia", "Zoan");
		assertThat(page.getContent().get(1).names()).containsExactly(entry("it", "Paramisha"));
		assertThat(page.getContent().getFirst().id()).isEqualTo(kodai);
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void theLinkableTypesAreNarrowedByWhatTheirRomajiOrANameContainsAndPaged() {
		stored();

		assertThat(this.links.linkable("zoo", 0, 10).getContent()).extracting(TypeReference::romaji)
			.containsExactly("Zoan");
		assertThat(this.links.linkable("PARAM", 0, 10).getContent()).extracting(TypeReference::romaji)
			.containsExactly("Paramecia");
		var second = this.links.linkable(null, 1, 1);
		assertThat(second.getContent()).extracting(TypeReference::romaji).containsExactly("Zoan");
		assertThat(second.getTotalPages()).isEqualTo(2);
		assertThat(this.links.linkable("rogia", 0, 10).getContent()).isEmpty();
	}

	@Test
	void theTypeAFruitPointsToIsAsItWasLastApprovedNotAsItIsWritten() {
		typeVersion(this.paramecia, 2, DRAFT, "Paramecia Draft", "Bozza");
		stored();

		Map<UUID, TypeReference> references = this.links.referencesOf(List.of(this.paramecia, this.zoan, this.logia));

		assertThat(references).containsOnlyKeys(this.paramecia, this.zoan);
		assertThat(references.get(this.paramecia))
			.isEqualTo(new TypeReference(this.paramecia, "Paramecia", Map.of("it", "Paramisha")));
	}

	private List<UUID> contentIds(Set<Permission> caller, UUID type) {
		var filter = new ContentFilter(null, null, null, null).relatedTo("type", type);
		return this.fruits.list(caller, this.chopper, filter, PageRequest.of(0, 20), "en")
			.map(ContentSummary::contentId)
			.getContent();
	}

	private long totalOf(Set<Permission> caller, UUID type) {
		var filter = new ContentFilter(null, null, null, null).relatedTo("type", type);
		return this.fruits.list(caller, this.chopper, filter, PageRequest.of(0, 20), "en").getTotalElements();
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

	private UUID typeContent(String romaji, String italianName, VersionStatus status) {
		UUID contentId = content(EntityType.DEVIL_FRUIT_TYPE);
		typeVersion(contentId, 1, status, romaji, italianName);
		return contentId;
	}

	private void typeVersion(UUID contentId, int number, VersionStatus status, String romaji, String italianName) {
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, number, status));
		version.setRomaji(romaji);
		version.getTranslations().put("it", new TranslationEmbeddable(italianName, "Descrizione", "Pro", "Contro"));
		this.typeRepository.save(version);
	}

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
