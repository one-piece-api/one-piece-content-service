package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
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
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.image.ImageNormalizer;
import dev.onepieceapi.contentservice.service.image.ImageService;
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

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.service.affiliation.AffiliationFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.affiliation.AffiliationFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.affiliation.AffiliationFixtures.READER;
import static dev.onepieceapi.contentservice.service.image.TestImages.ENCODER;
import static dev.onepieceapi.contentservice.service.image.TestImages.opaquePng;
import static dev.onepieceapi.contentservice.service.image.TestImages.transparentPng;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The emblem of an affiliation against a real PostgreSQL (Testcontainers), by the same
 * mechanism as the image of a Devil Fruit and the poster of a bounty (implementation plan
 * of the Affiliation, D5), so only what its profile asks is covered here: a square
 * canvas, a larger upload than the canvas, a transparent background; and that a replaced
 * or discarded emblem is deleted once no version uses it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class AffiliationEmblemIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	/** The affiliation profile as configured: square, transparency asked. */
	private static final ImageProperties EMBLEM_IMAGES = new ImageProperties("/api/content",
			Map.of(EntityType.AFFILIATION,
					new ImageProperties.Profile(400, 400, 512, 512, 0.1, DataSize.ofMegabytes(5), 25_000_000, 5)));

	@Autowired
	private AffiliationVersionRepository affiliationRepository;

	@Autowired
	private SpeciesVersionRepository speciesRepository;

	@Autowired
	private CharacterVersionRepository characterRepository;

	@Autowired
	private BountyVersionRepository bountyRepository;

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

	private AffiliationFixtures fixtures;

	private AffiliationService service;

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(AffiliationFixtures.NOW, ZoneOffset.UTC);
		var store = new JpaImageStore(this.imageRepository, clock);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(ENCODER), store, EMBLEM_IMAGES);
		this.fixtures = AffiliationFixtures.builder()
			.contents(this.contentRepository)
			.affiliations(this.affiliationRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.characters(this.characterRepository)
			.firstAppearance(OnlineChapters.onlineChapter(this.contentRepository, this.chapterRepository))
			.build();
		this.service = this.fixtures.service(images);
		this.imageService = new ImageService(store, this.devilFruitRepository, this.volumeRepository,
				this.bountyRepository, this.affiliationRepository, this.speciesRepository, this.characterRepository);
	}

	@Test
	void aTransparentSquareEmblemIsKeptAsAWebpOfTheCanvasAndEditsKeepIt() {
		var content = this.service.create(EDITOR, CHOPPER, affiliation("Mugiwara no Ichimi"),
				ImageChange.replaceWith(transparentPng(1024, 1024)));
		stored();
		String emblemId = emblemOf(content.id());

		this.service.edit(EDITOR, CHOPPER, content.id(), 1, affiliation("Mugiwara no Ichimi"));
		stored();

		assertThat(emblemOf(content.id())).isEqualTo(emblemId);
		var image = this.imageService.get(EDITOR, emblemId);
		assertThat(image.contentType()).isEqualTo("image/webp");
		assertThat(image.width()).isEqualTo(400);
		assertThat(image.height()).isEqualTo(400);
	}

	@Test
	void anEmblemTooSmallNotSquareOrWithoutTransparencyIsRefusedWithItsReasonAndSavesNothing() {
		var small = refusal("Kaigun", transparentPng(400, 400));
		var portrait = refusal("Kaigun", transparentPng(640, 800));
		var opaque = refusal("Kaigun", opaquePng(512, 512));
		stored();

		assertThat(small.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_TOO_SMALL);
		assertThat(portrait.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_WRONG_RATIO);
		assertThat(opaque.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_NOT_TRANSPARENT);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM affiliation_version", Long.class)).isZero();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aRemovedEmblemIsDeletedAndADiscardedDraftTakesItsEmblemWithIt() {
		var removed = this.service.create(EDITOR, CHOPPER, affiliation("Kaigun"),
				ImageChange.replaceWith(transparentPng(512, 512)));
		stored();
		this.service.edit(EDITOR, CHOPPER, removed.id(), 1, affiliation("Kaigun"), ImageChange.REMOVE);
		stored();
		assertThat(emblemOf(removed.id())).isNull();
		assertThat(this.imageRepository.count()).isZero();

		var discarded = this.service.create(EDITOR, CHOPPER, affiliation("Mugiwara no Ichimi"),
				ImageChange.replaceWith(transparentPng(512, 512)));
		stored();
		this.service.delete(EDITOR, CHOPPER, discarded.id(), 1);
		stored();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void anEmblemIsSeenOnlyByWhoeverSeesAVersionUsingIt() {
		var content = this.service.create(EDITOR, CHOPPER, affiliation("Kaigun"),
				ImageChange.replaceWith(transparentPng(512, 512)));
		stored();
		String emblemId = emblemOf(content.id());

		assertThat(this.imageService.get(EDITOR, emblemId).id()).isEqualTo(emblemId);
		assertThatThrownBy(() -> this.imageService.get(READER, emblemId)).isInstanceOf(ImageNotFoundException.class);
	}

	private ImageRefusedException refusal(String romaji, byte[] upload) {
		return catchThrowableOfType(ImageRefusedException.class,
				() -> this.service.create(EDITOR, CHOPPER, affiliation(romaji), ImageChange.replaceWith(upload)));
	}

	private Affiliation affiliation(String romaji) {
		return this.fixtures.complete(romaji);
	}

	private String emblemOf(UUID contentId) {
		return this.service.getVersion(EDITOR, CHOPPER, contentId, 1).body().imageId();
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
