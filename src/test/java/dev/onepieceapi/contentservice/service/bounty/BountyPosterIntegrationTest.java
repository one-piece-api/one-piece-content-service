package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ImageRepository;
import dev.onepieceapi.contentservice.persistence.repository.JpaImageStore;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.image.ImageNormalizer;
import dev.onepieceapi.contentservice.service.image.ImageService;
import dev.onepieceapi.contentservice.service.image.PngImageEncoder;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.READER;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.REVIEWER;
import static dev.onepieceapi.contentservice.service.image.TestImages.drawn;
import static dev.onepieceapi.contentservice.service.image.TestImages.opaquePng;
import static dev.onepieceapi.contentservice.service.image.TestImages.png;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The poster of a bounty against a real PostgreSQL (Testcontainers), from upload to
 * reading (implementation plan of the Bounty, D4) by the same mechanism as the image of a
 * Devil Fruit and the cover of a volume, covered by
 * {@code DevilFruitImageIntegrationTest} and {@code VolumeCoverIntegrationTest}: stored
 * with the draft as a PNG of the canvas, shared by identical uploads, kept by new
 * versions, deleted as soon as no version uses it, and seen only by whoever sees a
 * version using it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class BountyPosterIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	/** The bounty profile as configured: the canvas of a cover, no transparency asked. */
	private static final ImageProperties POSTER_IMAGES = new ImageProperties("/api/content", Map.of(EntityType.BOUNTY,
			new ImageProperties.Profile(420, 600, 560, 800, 0.1, DataSize.ofMegabytes(5), 25_000_000, 0)));

	@Autowired
	private BountyVersionRepository bountyRepository;

	@Autowired
	private AffiliationVersionRepository affiliationRepository;

	@Autowired
	private SpeciesVersionRepository speciesRepository;

	@Autowired
	private CharacterVersionRepository characterRepository;

	@Autowired
	private VolumeVersionRepository volumeRepository;

	@Autowired
	private DevilFruitVersionRepository devilFruitRepository;

	@Autowired
	private ChapterVersionRepository chapterRepository;

	@Autowired
	private EpisodeVersionRepository episodeRepository;

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

	private BountyFixtures fixtures;

	private BountyService service;

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(BountyFixtures.NOW, ZoneOffset.UTC);
		var store = new JpaImageStore(this.imageRepository, clock);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(new PngImageEncoder()), store,
				POSTER_IMAGES);
		this.fixtures = new BountyFixtures(this.contentRepository, this.bountyRepository, this.chapterRepository,
				this.episodeRepository, this.contentVersionRepository, this.languageRepository,
				this.auditLogRepository);
		this.service = this.fixtures.service(images);
		this.imageService = new ImageService(store, this.devilFruitRepository, this.volumeRepository,
				this.bountyRepository, this.affiliationRepository, this.speciesRepository, this.characterRepository);
	}

	@Test
	void aNewBountyKeepsItsUploadAsAPngOfTheCanvasAndEditsKeepIt() {
		var content = this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(1120, 1600)));
		stored();
		String posterId = posterOf(content.id(), 1);

		this.service.edit(EDITOR, CHOPPER, content.id(), 1, bounty(1));
		this.service.edit(EDITOR, CHOPPER, content.id(), 1, bounty(1), ImageChange.KEEP);
		stored();

		assertThat(posterOf(content.id(), 1)).isEqualTo(posterId);
		var image = this.imageService.get(EDITOR, posterId);
		assertThat(image.contentType()).isEqualTo("image/png");
		assertThat(image.width()).isEqualTo(420);
		assertThat(image.height()).isEqualTo(600);
	}

	@Test
	void aBountyWithoutAPosterIsFine() {
		var content = this.service.create(EDITOR, CHOPPER, bounty(1));
		stored();

		assertThat(posterOf(content.id(), 1)).isNull();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aPosterThatIsTheLeastAcceptedSizeAndNeedsNoTransparencyIsAccepted() {
		var content = this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(560, 800)));
		stored();

		assertThat(posterOf(content.id(), 1)).isNotNull();
	}

	@Test
	void aLandscapeOrTooSmallPosterIsRefusedWithItsReasonAndSavesNothing() {
		var landscape = catchThrowableOfType(ImageRefusedException.class,
				() -> this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(1600, 1120))));
		var small = catchThrowableOfType(ImageRefusedException.class,
				() -> this.service.create(EDITOR, CHOPPER, bounty(2), ImageChange.replaceWith(opaquePng(420, 600))));
		stored();

		assertThat(landscape.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_WRONG_RATIO);
		assertThat(small.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_TOO_SMALL);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM bounty_version", Long.class)).isZero();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aReplacedPosterIsDeletedOnceNoVersionUsesItAndARemovedOneToo() {
		var content = this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String first = posterOf(content.id(), 1);

		this.service.edit(EDITOR, CHOPPER, content.id(), 1, bounty(1), ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		assertThat(posterOf(content.id(), 1)).isEqualTo(first);

		this.service.edit(EDITOR, CHOPPER, content.id(), 1, bounty(1), ImageChange.replaceWith(png(withBlueCorner())));
		stored();
		assertThat(posterOf(content.id(), 1)).isNotEqualTo(first);
		assertThat(this.imageRepository.existsById(first)).isFalse();
		assertThat(this.imageRepository.count()).isEqualTo(1);

		this.service.edit(EDITOR, CHOPPER, content.id(), 1, bounty(1), ImageChange.REMOVE);
		stored();
		assertThat(posterOf(content.id(), 1)).isNull();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void theSameUploadIsOnePosterKeptUntilItsLastDraftIsDiscarded() {
		var first = this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(560, 800)));
		var second = this.service.create(EDITOR, CHOPPER, bounty(2), ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String shared = posterOf(first.id(), 1);
		assertThat(posterOf(second.id(), 1)).isEqualTo(shared);
		assertThat(this.imageRepository.count()).isEqualTo(1);

		this.service.delete(EDITOR, CHOPPER, first.id(), 1);
		stored();
		assertThat(this.imageRepository.existsById(shared)).isTrue();

		this.service.delete(EDITOR, CHOPPER, second.id(), 1);
		stored();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aNewVersionTakesThePosterOfItsBaseWhichStaysWhileTheBaseUsesIt() {
		var content = this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String online = posterOf(content.id(), 1);
		publish(content.id());

		this.service.openNewVersion(EDITOR, CHOPPER, content.id(), 1);
		stored();
		assertThat(posterOf(content.id(), 2)).isEqualTo(online);

		this.service.edit(EDITOR, CHOPPER, content.id(), 2, bounty(1), ImageChange.replaceWith(png(withBlueCorner())));
		stored();

		assertThat(posterOf(content.id(), 2)).isNotEqualTo(online);
		assertThat(this.imageRepository.existsById(online)).isTrue();
	}

	@Test
	void aNewVersionDifferingOnlyInItsPosterIsNotIdentical() {
		var content = this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		publish(content.id());
		this.service.openNewVersion(EDITOR, CHOPPER, content.id(), 1);
		stored();

		assertThatThrownBy(() -> this.service.submit(EDITOR, CHOPPER, content.id(), 2))
			.isInstanceOf(VersionIdenticalException.class);

		this.service.edit(EDITOR, CHOPPER, content.id(), 2, bounty(1), ImageChange.replaceWith(png(withBlueCorner())));
		stored();

		assertThat(this.service.submit(EDITOR, CHOPPER, content.id(), 2).version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void aPosterIsSeenOnlyByWhoeverSeesAVersionUsingIt() {
		var content = this.service.create(EDITOR, CHOPPER, bounty(1), ImageChange.replaceWith(opaquePng(560, 800)));
		stored();
		String posterId = posterOf(content.id(), 1);

		assertThat(this.imageService.get(EDITOR, posterId).id()).isEqualTo(posterId);
		assertThatThrownBy(() -> this.imageService.get(REVIEWER, posterId)).isInstanceOf(ImageNotFoundException.class);
		assertThatThrownBy(() -> this.imageService.get(READER, posterId)).isInstanceOf(ImageNotFoundException.class);

		this.service.submit(EDITOR, CHOPPER, content.id(), 1);
		stored();

		assertThat(this.imageService.get(REVIEWER, posterId).id()).isEqualTo(posterId);
		assertThatThrownBy(() -> this.imageService.get(READER, posterId)).isInstanceOf(ImageNotFoundException.class);
	}

	private Bounty bounty(long amount) {
		return this.fixtures.complete("Monkey D. Luffy", amount, null);
	}

	private void publish(UUID contentId) {
		this.jdbc.update("UPDATE content_version SET status = 'PUBLISHED' WHERE content_id = ? AND version_number = 1",
				contentId);
		stored();
	}

	private String posterOf(UUID contentId, int number) {
		return this.service.getVersion(EDITOR, CHOPPER, contentId, number).body().imageId();
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
