package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ImageRepository;
import dev.onepieceapi.contentservice.persistence.repository.JpaImageStore;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.image.ImageNormalizer;
import dev.onepieceapi.contentservice.service.image.ImageService;
import dev.onepieceapi.contentservice.service.image.PngImageEncoder;
import dev.onepieceapi.contentservice.service.validation.ImageValidator;
import dev.onepieceapi.contentservice.service.validation.VolumeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.service.image.TestImages.drawn;
import static dev.onepieceapi.contentservice.service.image.TestImages.opaquePng;
import static dev.onepieceapi.contentservice.service.image.TestImages.png;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The cover of a volume against a real PostgreSQL (Testcontainers), from upload to
 * reading (implementation plan of the story structure, S6) by the same mechanism as the
 * image of a Devil Fruit, covered by {@code DevilFruitImageIntegrationTest}: stored with
 * the draft as a PNG of the canvas, shared by identical uploads, kept by new versions,
 * deleted as soon as no version uses it, and seen only by whoever sees a version using
 * it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class VolumeCoverIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> READER = Set.of(Permission.CONTENT_READ);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	@Autowired
	private VolumeVersionRepository volumeRepository;

	@Autowired
	private BountyVersionRepository bountyRepository;

	@Autowired
	private AffiliationVersionRepository affiliationRepository;

	@Autowired
	private SpeciesVersionRepository speciesRepository;

	@Autowired
	private ChapterVersionRepository chapterRepository;

	@Autowired
	private DevilFruitVersionRepository devilFruitRepository;

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

	private VolumeService service;

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var store = new JpaImageStore(this.imageRepository, clock);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(new PngImageEncoder()), store,
				StoryFixtures.VOLUME_IMAGES);
		this.service = new VolumeService(this.contentVersionRepository, this.contentRepository,
				new AuditLogService(this.auditLogRepository, clock), clock, images,
				new VolumeDefinition(this.volumeRepository,
						new VolumeValidator(this.volumeRepository, this.languageRepository),
						new VolumeRules(this.contentRepository, this.chapterRepository, new RulesProperties(5))));
		this.imageService = new ImageService(store, this.devilFruitRepository, this.volumeRepository,
				this.bountyRepository, this.affiliationRepository, this.speciesRepository);
	}

	@Test
	void aNewVolumeKeepsItsUploadAsAPngOfTheCanvasAndEditsKeepIt() {
		var content = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(opaquePng(1120, 1600)));
		stored();
		String coverId = coverOf(content.id(), 1);

		this.service.edit(EDITOR, this.nami, content.id(), 1, StoryFixtures.completeVolume(1));
		this.service.edit(EDITOR, this.nami, content.id(), 1, StoryFixtures.completeVolume(1), ImageChange.KEEP);
		stored();

		assertThat(coverOf(content.id(), 1)).isEqualTo(coverId);
		var image = this.imageService.get(EDITOR, coverId);
		assertThat(image.contentType()).isEqualTo("image/png");
		assertThat(image.width()).isEqualTo(420);
		assertThat(image.height()).isEqualTo(600);
	}

	@Test
	void aVolumeWithoutACoverIsFine() {
		var content = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(1));
		stored();

		assertThat(coverOf(content.id(), 1)).isNull();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aRefusedUploadSavesNothing() {
		var refused = catchThrowableOfType(ImageRefusedException.class, () -> this.service.create(EDITOR, this.nami,
				StoryFixtures.completeVolume(1), ImageChange.replaceWith(opaquePng(1600, 1120))));
		stored();

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_WRONG_RATIO);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM content", Long.class)).isZero();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aReplacedCoverIsDeletedOnceNoVersionUsesItAndARemovedOneToo() {
		var content = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String first = coverOf(content.id(), 1);

		this.service.edit(EDITOR, this.nami, content.id(), 1, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		assertThat(coverOf(content.id(), 1)).isEqualTo(first);

		this.service.edit(EDITOR, this.nami, content.id(), 1, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(png(withBlueCorner())));
		stored();
		assertThat(coverOf(content.id(), 1)).isNotEqualTo(first);
		assertThat(this.imageRepository.existsById(first)).isFalse();
		assertThat(this.imageRepository.count()).isEqualTo(1);

		this.service.edit(EDITOR, this.nami, content.id(), 1, StoryFixtures.completeVolume(1), ImageChange.REMOVE);
		stored();
		assertThat(coverOf(content.id(), 1)).isNull();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void theSameUploadIsOneCoverKeptUntilItsLastDraftIsDiscarded() {
		var first = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(opaquePng(560, 800)));
		var second = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(2),
				ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String shared = coverOf(first.id(), 1);
		assertThat(coverOf(second.id(), 1)).isEqualTo(shared);
		assertThat(this.imageRepository.count()).isEqualTo(1);

		this.service.delete(EDITOR, this.nami, first.id(), 1);
		stored();
		assertThat(this.imageRepository.existsById(shared)).isTrue();

		this.service.delete(EDITOR, this.nami, second.id(), 1);
		stored();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aNewVersionTakesTheCoverOfItsBaseWhichStaysWhileTheBaseUsesIt() {
		var content = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String online = coverOf(content.id(), 1);
		publish(content.id());

		this.service.openNewVersion(EDITOR, this.nami, content.id(), 1);
		stored();
		assertThat(coverOf(content.id(), 2)).isEqualTo(online);

		this.service.edit(EDITOR, this.nami, content.id(), 2, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(png(withBlueCorner())));
		stored();

		assertThat(coverOf(content.id(), 2)).isNotEqualTo(online);
		assertThat(this.imageRepository.existsById(online)).isTrue();
	}

	@Test
	void aNewVersionDifferingOnlyInItsCoverIsNotIdentical() {
		var content = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		publish(content.id());
		this.service.openNewVersion(EDITOR, this.nami, content.id(), 1);
		stored();

		assertThatThrownBy(() -> this.service.submit(EDITOR, this.nami, content.id(), 2))
			.isInstanceOf(VersionIdenticalException.class);

		this.service.edit(EDITOR, this.nami, content.id(), 2, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(png(withBlueCorner())));
		stored();

		assertThat(this.service.submit(EDITOR, this.nami, content.id(), 2).version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void aCoverIsSeenOnlyByWhoeverSeesAVersionUsingIt() {
		var content = this.service.create(EDITOR, this.nami, StoryFixtures.completeVolume(1),
				ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String coverId = coverOf(content.id(), 1);

		assertThat(this.imageService.get(EDITOR, coverId).id()).isEqualTo(coverId);
		assertThatThrownBy(() -> this.imageService.get(REVIEWER, coverId)).isInstanceOf(ImageNotFoundException.class);
		assertThatThrownBy(() -> this.imageService.get(READER, coverId)).isInstanceOf(ImageNotFoundException.class);

		this.service.submit(EDITOR, this.nami, content.id(), 1);
		stored();

		assertThat(this.imageService.get(REVIEWER, coverId).id()).isEqualTo(coverId);
		assertThatThrownBy(() -> this.imageService.get(READER, coverId)).isInstanceOf(ImageNotFoundException.class);
	}

	private void publish(UUID contentId) {
		this.jdbc.update("UPDATE content_version SET status = 'PUBLISHED' WHERE content_id = ? AND version_number = 1",
				contentId);
		stored();
	}

	private String coverOf(UUID contentId, int number) {
		Volume body = this.service.getVersion(EDITOR, this.nami, contentId, number).version().body();
		return body.coverImageId();
	}

	/** The usual disc with a blue corner: another image. */
	private static BufferedImage withBlueCorner() {
		var image = drawn(560, 800, Color.WHITE);
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

}
