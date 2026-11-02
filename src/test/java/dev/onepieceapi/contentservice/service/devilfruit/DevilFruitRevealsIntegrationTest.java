package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitFixtures;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitKnowledge;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeFixtures;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.content.ReaderAxisRules;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeRules;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeService;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.contentservice.service.exception.SlugAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import dev.onepieceapi.contentservice.service.validation.ParagraphRules;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The reveals of a Devil Fruit (workflow 3.7.1; reveals plan R4), against a real
 * PostgreSQL (Testcontainers). The reader first meets the Gomu Gomu no Mi in chapter 1
 * and episode 1, a Paramecia; chapter 1044 and episode 1071 reveal that it is the Hito
 * Hito no Mi, Model: Nika, a Mythical Zoan (One Piece Wiki, Hito Hito no Mi, Model:
 * Nika).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitRevealsIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH,
			Permission.CONTENT_RETIRE);

	private static final UUID MYTHICAL = UUID.randomUUID();

	private static final String CURRENT_ROMAJI = "Hito Hito no Mi, Model: Nika";

	private static final String INITIAL_ROMAJI = "Gomu Gomu no Mi";

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private ChapterVersionRepository chapterRepository;

	@Autowired
	private EpisodeVersionRepository episodeRepository;

	@Autowired
	private CharacterVersionRepository characterRepository;

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

	private StoryPoint firstAppearance;

	private StoryPoint nikaRevealed;

	private UUID paramecia;

	private UUID zoan;

	@BeforeEach
	void setUp() {
		this.firstAppearance = point(1, 1);
		this.nikaRevealed = point(1044, 1071);
		var readerAxisRules = new ReaderAxisRules(this.contentRepository, this.chapterRepository,
				this.episodeRepository);
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		this.fruits = new DevilFruitService(new DevilFruitDefinition(this.fruitRepository,
				new DevilFruitValidator(this.fruitRepository, this.languageRepository, this.typeRepository,
						this.chapterRepository, this.episodeRepository, new ParagraphRules(readerAxisRules)),
				new DevilFruitRules(this.contentRepository, this.typeRepository, this.chapterRepository,
						this.episodeRepository, this.characterRepository, readerAxisRules, new RulesProperties(5))),
				this.contentVersionRepository, this.contentRepository, auditLogService, clock,
				mock(ContentImages.class));
		this.types = new DevilFruitTypeService(this.typeRepository, this.contentVersionRepository,
				this.contentRepository,
				new DevilFruitTypeValidator(this.typeRepository, this.languageRepository, this.chapterRepository,
						this.episodeRepository, new ParagraphRules(readerAxisRules)),
				new DevilFruitTypeRules(this.contentRepository, this.fruitRepository, this.chapterRepository,
						this.episodeRepository, readerAxisRules, new RulesProperties(5)),
				auditLogService, clock);
		this.paramecia = typeContent(PUBLISHED, type("Choujin"));
		this.zoan = typeContent(PUBLISHED, type("Dobutsu").toBuilder().subcategories(List.of(mythical())).build());
		stored();
	}

	// ---- Saving

	@Test
	void gomuGomuIsLearnedToBeTheHitoHitoNoMiModelNikaOnBothAxesAndReadsBackAsSaved() {
		var created = this.fruits.create(EDITOR, this.chopper, nika());
		stored();

		var read = this.fruits.getVersion(EDITOR, this.chopper, created.id(), 1).version().body();
		assertThat(read).isEqualTo(nika().normalized());
		assertThat(read.reveals().getFirst().revealedAt()).isEqualTo(this.nikaRevealed);
		assertThat(read.initialKnowledge().names()).containsOnly(entry("en", "Gum-Gum Fruit"),
				entry("it", "Frutto Gom Gom"));
	}

	@Test
	void aRevealIsRefusedAtTheFirstAppearanceAndTwoRevealsOfTheSameFieldNeverShareAPoint() {
		var written = nika().toBuilder()
			.reveals(List.of(reveal(this.firstAppearance, DevilFruitField.TYPE),
					reveal(this.nikaRevealed, DevilFruitField.NAME, DevilFruitField.ROMAJI),
					reveal(this.nikaRevealed, DevilFruitField.ROMAJI)))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.fruits.create(EDITOR, this.chopper, written));

		assertThat(violations(refused)).extracting(FieldViolation::field, FieldViolation::message)
			.containsExactlyInAnyOrder(tuple("reveals[0].revealedAt.chapter", "must be after the first appearance"),
					tuple("reveals[0].revealedAt.episode", "must be after the first appearance"),
					tuple("reveals[2].revealedAt.chapter",
							"must not share its point with an earlier reveal of romaji on the chapter axis"),
					tuple("reveals[2].revealedAt.episode",
							"must not share its point with an earlier reveal of romaji on the episode axis"));
	}

	@Test
	void theInitialTypeMustBeLinkableAndHoldOnlyASubcategoryOfItsOwn() {
		UUID draftType = typeContent(DRAFT, type("Shizen"));
		stored();
		var written = nika().toBuilder()
			.initialKnowledge(new DevilFruitKnowledge(INITIAL_ROMAJI, draftType, null, initialNames()))
			.reveals(List.of(new Reveal<>(this.nikaRevealed, EnumSet.allOf(DevilFruitField.class),
					new DevilFruitKnowledge(null, this.paramecia, MYTHICAL, Map.of()), titles())))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.fruits.create(EDITOR, this.chopper, written));

		assertThat(violations(refused)).extracting(FieldViolation::field)
			.containsExactlyInAnyOrder("initialKnowledge.type", "reveals[0].subcategory");
	}

	@Test
	void submittingAsksEveryTitleAndEveryInitialNameInEveryLanguage() {
		var written = nika().toBuilder()
			.initialKnowledge(
					new DevilFruitKnowledge(INITIAL_ROMAJI, this.paramecia, null, Map.of("it", "Frutto Gom Gom")))
			.reveals(List.of(new Reveal<>(this.nikaRevealed, EnumSet.allOf(DevilFruitField.class),
					DevilFruitKnowledge.NONE, Map.of("en", "The fruit of Nika"))))
			.build();
		var created = this.fruits.create(EDITOR, this.chopper, written);
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.fruits.submit(EDITOR, this.chopper, created.id(), 1));

		assertThat(violations(refused)).extracting(FieldViolation::field)
			.containsExactly("initialKnowledge.translations[en].name", "reveals[0].titles[it]");
	}

	// ---- Slugs

	@Test
	void goingOnlineRegistersTheSlugOfEveryRomajiAndNoOtherFruitTakesOne() {
		UUID nika = fruitContent(READY_TO_PUBLISH, nika());
		stored();

		this.fruits.publish(PUBLISHER, this.chopper, nika, 1);
		stored();

		assertThat(slugsOf(nika)).containsExactlyInAnyOrder(slugOf(CURRENT_ROMAJI), slugOf(INITIAL_ROMAJI));
		var refused = catchThrowableOfType(SlugAlreadyUsedException.class,
				() -> this.fruits.create(EDITOR, this.chopper, fruit(INITIAL_ROMAJI, this.paramecia)));
		assertThat(violations(refused)).extracting(FieldViolation::field).containsExactly("romaji");
	}

	@Test
	void aRomajiRevealedByAnotherFruitIsRefusedNamingWhereItIsWritten() {
		fruitContent(DRAFT, nika());
		stored();
		var written = fruit("Mera Mera no Mi", this.paramecia).toBuilder()
			.initialKnowledge(new DevilFruitKnowledge(CURRENT_ROMAJI, null, null, initialNames()))
			.reveals(List.of(reveal(this.nikaRevealed, DevilFruitField.NAME, DevilFruitField.ROMAJI)))
			.build();

		var refused = catchThrowableOfType(SlugAlreadyUsedException.class,
				() -> this.fruits.create(EDITOR, this.chopper, written));

		assertThat(violations(refused)).extracting(FieldViolation::field).containsExactly("initialKnowledge.romaji");
	}

	// ---- Going online and retiring

	@Test
	void aFruitDoesNotGoOnlineWhileItsInitialTypeIsNotNamingWhereItIs() {
		UUID approvedType = typeContent(READY_TO_PUBLISH, type("Shizen"));
		var written = nika().toBuilder()
			.initialKnowledge(new DevilFruitKnowledge(INITIAL_ROMAJI, approvedType, null, initialNames()))
			.build();
		UUID nika = fruitContent(READY_TO_PUBLISH, written);
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fruits.publish(PUBLISHER, this.chopper, nika, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.TYPE_NOT_ONLINE);
		assertThat(detailOf(refused)).containsEntry("initialKnowledge", true).containsEntry("typeId", approvedType);
	}

	@Test
	void aTypeIsNotRetiredWhileAnOnlineFruitIsBelievedToBeOfItAndTheRevealPointsCount() {
		fruitContent(PUBLISHED, nika());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.types.retire(PUBLISHER, this.chopper, this.paramecia, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_FRUITS_LINKED);
		assertThat(this.fruitRepository.countOnlineWithPointIn(this.nikaRevealed.chapterContentId())).isOne();
		assertThat(this.fruitRepository.countOnlineWithPointIn(this.nikaRevealed.episodeContentId())).isOne();
	}

	// ---- Helpers

	/**
	 * Its current values the reveal's: the reader believes the Gomu Gomu no Mi, a
	 * Paramecia, until Nika.
	 */
	private DevilFruit nika() {
		return fruit(CURRENT_ROMAJI, this.zoan).toBuilder()
			.subcategoryId(MYTHICAL)
			.firstAppearance(this.firstAppearance)
			.initialKnowledge(new DevilFruitKnowledge(INITIAL_ROMAJI, this.paramecia, null, initialNames()))
			.reveals(List
				.of(reveal(this.nikaRevealed, DevilFruitField.NAME, DevilFruitField.ROMAJI, DevilFruitField.TYPE)))
			.build();
	}

	private DevilFruit fruit(String romaji, UUID type) {
		return DevilFruitFixtures.complete(romaji, type, null, this.firstAppearance)
			.toBuilder()
			.translations(Map.of("it", new DevilFruitTranslation(romaji + " (it)"), "en",
					new DevilFruitTranslation(romaji + " (en)")))
			.build();
	}

	/** Revealing the current value of each field. */
	private static Reveal<DevilFruitField, DevilFruitKnowledge> reveal(StoryPoint point, DevilFruitField... fields) {
		return new Reveal<>(point, Set.of(fields), DevilFruitKnowledge.NONE, titles());
	}

	private static Map<String, String> titles() {
		return Map.of("it", "Il frutto di Nika", "en", "The fruit of Nika");
	}

	private static Map<String, String> initialNames() {
		return Map.of("it", "Frutto Gom Gom", "en", "Gum-Gum Fruit");
	}

	private DevilFruitType type(String romaji) {
		return DevilFruitTypeFixtures.complete(romaji, StoryPoint.ofChapter(this.firstAppearance.chapterContentId()));
	}

	private static DevilFruitTypeSubcategory mythical() {
		return new DevilFruitTypeSubcategory(MYTHICAL,
				Map.of("it", new DevilFruitTypeSubcategoryTranslation("Mitologico", "Descrizione"), "en",
						new DevilFruitTypeSubcategoryTranslation("Mythical", "Description")));
	}

	private StoryPoint point(int chapter, int episode) {
		return new StoryPoint(
				OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED, chapter),
				OnlineChapters.episode(this.contentRepository, this.episodeRepository, PUBLISHED, episode));
	}

	private String slugOf(String romaji) {
		return this.fruitRepository.slugOf(romaji).orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private List<String> slugsOf(UUID contentId) {
		return this.entityManager.getEntityManager()
			.createNativeQuery("select slug from content_slug where content_id = :contentId")
			.setParameter("contentId", contentId)
			.getResultList();
	}

	@SuppressWarnings("unchecked")
	private static List<FieldViolation> violations(DomainException refused) {
		return (List<FieldViolation>) refused.getDetails().get("errors");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> detailOf(VersionActionBlockedException refused) {
		return (Map<String, Object>) refused.getDetails().get("detail");
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID content(EntityType entityType) {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	private UUID typeContent(VersionStatus status, DevilFruitType body) {
		UUID contentId = content(EntityType.DEVIL_FRUIT_TYPE);
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, status));
		DevilFruitTypeVersionMapper.rewrite(version, body, EARLIER);
		this.typeRepository.save(version);
		return contentId;
	}

	private UUID fruitContent(VersionStatus status, DevilFruit body) {
		UUID contentId = content(EntityType.DEVIL_FRUIT);
		var version = new DevilFruitVersionEntity(workflow(contentId, status));
		DevilFruitVersionMapper.rewrite(version, body, EARLIER);
		this.fruitRepository.save(version);
		return contentId;
	}

	private ContentVersionEntity workflow(UUID contentId, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(this.chopper))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
	}

}
