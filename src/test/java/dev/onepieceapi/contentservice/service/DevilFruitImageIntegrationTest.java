package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
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
import dev.onepieceapi.contentservice.persistence.repository.ImageRepository;
import dev.onepieceapi.contentservice.persistence.repository.JpaImageStore;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.image.ImageNormalizer;
import dev.onepieceapi.contentservice.service.image.PngImageEncoder;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import dev.onepieceapi.contentservice.service.validation.ImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.service.image.TestImages.drawn;
import static dev.onepieceapi.contentservice.service.image.TestImages.opaquePng;
import static dev.onepieceapi.contentservice.service.image.TestImages.png;
import static dev.onepieceapi.contentservice.service.image.TestImages.transparentPng;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The image of a Devil Fruit against a real PostgreSQL (Testcontainers), from upload to
 * reading (implementation plan of the Devil Fruit, D5, D6): stored with the draft, shared
 * by identical uploads, kept by new versions, deleted as soon as no version uses it - and
 * never while one does, whatever the service does - and seen only by whoever sees a
 * version using it.
 * <p>
 * Seeded for every test: the Paramecia type, online in v1.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitImageIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> READER = Set.of(Permission.CONTENT_READ);

	private static final ImageProperties PROPERTIES = new ImageProperties("/api/content", Map.of(EntityType.DEVIL_FRUIT,
			new ImageProperties.Profile(640, 800, 0.1, DataSize.ofMegabytes(5), 25_000_000, 5)));

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
	private ImageRepository imageRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entityManager;

	private DevilFruitService service;

	private ImageService imageService;

	private UUID paramecia;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new DevilFruitValidator(this.versionRepository, this.languageRepository, this.typeRepository);
		var store = new JpaImageStore(this.imageRepository, clock);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(new PngImageEncoder()), store,
				PROPERTIES);
		this.service = new DevilFruitService(
				new DevilFruitDefinition(this.versionRepository, validator,
						new DevilFruitRules(this.contentRepository, this.typeRepository)),
				this.contentVersionRepository, this.contentRepository,
				new AuditLogService(this.auditLogRepository, clock), clock, images);
		this.imageService = new ImageService(store, this.versionRepository);

		this.paramecia = this.entityManager
			.persist(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT_TYPE, EARLIER))
			.getId();
		var type = new DevilFruitTypeVersionEntity(workflow(this.paramecia, 1, PUBLISHED));
		type.setRomaji("Paramecia");
		type.getTranslations().put("it", new TranslationEmbeddable("Paramecia", "Descrizione", "Pro", "Contro"));
		this.typeRepository.save(type);
		stored();
	}

	@Test
	void aNewFruitKeepsItsUploadAsAPngOfTheCanvasAndEditsKeepIt() {
		var content = this.service.create(EDITOR, this.nami, written("Mera Mera"),
				ImageChange.replaceWith(transparentPng(1280, 1600)));
		stored();
		String imageId = imageOf(content.id(), 1);

		this.service.edit(EDITOR, this.nami, content.id(), 1, written("Mera Mera"), ImageChange.KEEP);
		stored();

		assertThat(imageOf(content.id(), 1)).isEqualTo(imageId);
		var image = this.imageService.get(EDITOR, imageId);
		assertThat(image.contentType()).isEqualTo("image/png");
		assertThat(image.width()).isEqualTo(640);
		assertThat(image.height()).isEqualTo(800);
	}

	@Test
	void aRefusedUploadSavesNothing() {
		var refused = catchThrowableOfType(ImageRefusedException.class, () -> this.service.create(EDITOR, this.nami,
				written("Mera Mera"), ImageChange.replaceWith(opaquePng(640, 800))));
		stored();

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_NOT_TRANSPARENT);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM content", Long.class)).isEqualTo(1);
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aReplacedImageIsDeletedOnceNoVersionUsesIt() {
		var content = this.service.create(EDITOR, this.nami, written("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();
		String first = imageOf(content.id(), 1);

		this.service.edit(EDITOR, this.nami, content.id(), 1, written("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();
		assertThat(imageOf(content.id(), 1)).isEqualTo(first);

		this.service.edit(EDITOR, this.nami, content.id(), 1, written("Mera Mera"),
				ImageChange.replaceWith(png(withBlueCorner())));
		stored();

		assertThat(imageOf(content.id(), 1)).isNotEqualTo(first);
		assertThat(this.imageRepository.existsById(first)).isFalse();
		assertThat(this.imageRepository.count()).isEqualTo(1);
	}

	@Test
	void aRemovedImageIsDeleted() {
		var content = this.service.create(EDITOR, this.nami, written("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();

		this.service.edit(EDITOR, this.nami, content.id(), 1, written("Mera Mera"), ImageChange.REMOVE);
		stored();

		assertThat(imageOf(content.id(), 1)).isNull();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void theSameUploadIsOneImageKeptUntilItsLastDraftIsDiscarded() {
		var mera = this.service.create(EDITOR, this.nami, written("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		var hie = this.service.create(EDITOR, this.nami, written("Hie Hie"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();
		String shared = imageOf(mera.id(), 1);
		assertThat(imageOf(hie.id(), 1)).isEqualTo(shared);
		assertThat(this.imageRepository.count()).isEqualTo(1);

		this.service.delete(EDITOR, this.nami, mera.id(), 1);
		stored();
		assertThat(this.imageRepository.existsById(shared)).isTrue();

		this.service.delete(EDITOR, this.nami, hie.id(), 1);
		stored();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aNewVersionTakesTheImageOfItsBaseWhichStaysWhileTheBaseUsesIt() {
		var content = this.service.create(EDITOR, this.chopper, complete("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();
		String online = imageOf(content.id(), 1);
		publish(content.id());

		this.service.openNewVersion(EDITOR, this.nami, content.id(), 1);
		stored();
		assertThat(imageOf(content.id(), 2)).isEqualTo(online);

		this.service.edit(EDITOR, this.nami, content.id(), 2, complete("Mera Mera"),
				ImageChange.replaceWith(png(withBlueCorner())));
		stored();

		assertThat(imageOf(content.id(), 2)).isNotEqualTo(online);
		assertThat(this.imageRepository.existsById(online)).isTrue();
	}

	@Test
	void aNewVersionDifferingOnlyInItsImageIsNotIdentical() {
		var content = this.service.create(EDITOR, this.chopper, complete("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();
		publish(content.id());
		this.service.openNewVersion(EDITOR, this.nami, content.id(), 1);
		stored();

		assertThatThrownBy(() -> this.service.submit(EDITOR, this.nami, content.id(), 2))
			.isInstanceOf(VersionIdenticalException.class);

		this.service.edit(EDITOR, this.nami, content.id(), 2, complete("Mera Mera"),
				ImageChange.replaceWith(png(withBlueCorner())));
		stored();

		assertThat(this.service.submit(EDITOR, this.nami, content.id(), 2).version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void anImageIsSeenOnlyByWhoeverSeesAVersionUsingIt() {
		var content = this.service.create(EDITOR, this.nami, complete("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();
		String imageId = imageOf(content.id(), 1);

		assertThat(this.imageService.get(EDITOR, imageId).id()).isEqualTo(imageId);
		assertThatThrownBy(() -> this.imageService.get(REVIEWER, imageId)).isInstanceOf(ImageNotFoundException.class);
		assertThatThrownBy(() -> this.imageService.get(READER, imageId)).isInstanceOf(ImageNotFoundException.class);

		this.service.submit(EDITOR, this.nami, content.id(), 1);
		stored();

		assertThat(this.imageService.get(REVIEWER, imageId).id()).isEqualTo(imageId);
		assertThatThrownBy(() -> this.imageService.get(READER, imageId)).isInstanceOf(ImageNotFoundException.class);
	}

	@Test
	void anUnknownImageIsNotFound() {
		assertThatThrownBy(() -> this.imageService.get(EDITOR, "0".repeat(64)))
			.isInstanceOf(ImageNotFoundException.class);
	}

	@Test
	void theForeignKeyRefusesToDeleteAnImageAVersionUses() {
		// What saves a draft that took the image while another let it go, at the same
		// time: the delete fails rather than leave the draft pointing at nothing.
		var content = this.service.create(EDITOR, this.nami, written("Mera Mera"),
				ImageChange.replaceWith(transparentPng(640, 800)));
		stored();
		String imageId = imageOf(content.id(), 1);

		assertThatThrownBy(() -> this.jdbc.update("DELETE FROM image WHERE id = ?", imageId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void publish(UUID contentId) {
		this.jdbc.update("UPDATE content_version SET status = 'PUBLISHED' WHERE content_id = ? AND version_number = 1",
				contentId);
		stored();
	}

	private String imageOf(UUID contentId, int number) {
		return this.service.getVersion(EDITOR, this.nami, contentId, number).version().body().imageId();
	}

	private DevilFruit written(String romaji) {
		return new DevilFruit(romaji, this.paramecia, Map.of());
	}

	private DevilFruit complete(String romaji) {
		return new DevilFruit(romaji, this.paramecia,
				Map.of("it", new DevilFruitTranslation(romaji + " it", "Descrizione", "Pro", "Contro"), "en",
						new DevilFruitTranslation(romaji + " no Mi", "Description", "Pros", "Cons")));
	}

	/** The usual disc with a blue corner: another image. */
	private static BufferedImage withBlueCorner() {
		var image = drawn(640, 800, null);
		var graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, 10, 10);
		graphics.dispose();
		return image;
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
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
