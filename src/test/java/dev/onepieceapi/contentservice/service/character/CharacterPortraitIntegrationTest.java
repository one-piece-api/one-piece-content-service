package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
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
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.image.ImageNormalizer;
import dev.onepieceapi.contentservice.service.image.ImageService;
import dev.onepieceapi.contentservice.service.image.PngImageEncoder;
import dev.onepieceapi.contentservice.service.validation.ImageValidator;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
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

import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.READER;
import static dev.onepieceapi.contentservice.service.image.TestImages.opaquePng;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The portrait of a character against a real PostgreSQL (Testcontainers), by the same
 * mechanism as the picture of a species (implementation plan of the Character, D7), so
 * only what its profile asks is covered here: a 4:5 canvas, a larger upload than the
 * canvas, no transparency required; and that a removed or discarded portrait is deleted
 * once no version uses it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterPortraitIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	/** The character profile as configured: portrait 4:5, no transparency asked. */
	private static final ImageProperties PORTRAIT_IMAGES = new ImageProperties("/api/content",
			Map.of(EntityType.CHARACTER,
					new ImageProperties.Profile(320, 400, 400, 500, 0.1, DataSize.ofMegabytes(5), 25_000_000, 0)));

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

	private CharacterFixtures fixtures;

	private CharacterService service;

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(CharacterFixtures.NOW, ZoneOffset.UTC);
		var store = new JpaImageStore(this.imageRepository, clock);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(new PngImageEncoder()), store,
				PORTRAIT_IMAGES);
		this.fixtures = CharacterFixtures.builder()
			.contents(this.contentRepository)
			.characters(this.characterRepository)
			.species(this.speciesRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.firstAppearance(OnlineChapters.onlineChapter(this.contentRepository, this.chapterRepository))
			.build();
		this.service = this.fixtures.service(images);
		this.imageService = new ImageService(store, this.devilFruitRepository, this.volumeRepository,
				this.bountyRepository, this.affiliationRepository, this.speciesRepository, this.characterRepository);
	}

	@Test
	void anOpaquePortraitIsKeptAsAPngOfTheCanvasAndEditsKeepIt() {
		var content = this.service.create(EDITOR, CHOPPER, character("Nami"),
				ImageChange.replaceWith(opaquePng(800, 1000)));
		stored();
		String portraitId = portraitOf(content.id());

		this.service.edit(EDITOR, CHOPPER, content.id(), 1, character("Nami"));
		stored();

		assertThat(portraitOf(content.id())).isEqualTo(portraitId);
		var image = this.imageService.get(EDITOR, portraitId);
		assertThat(image.contentType()).isEqualTo("image/png");
		assertThat(image.width()).isEqualTo(320);
		assertThat(image.height()).isEqualTo(400);
	}

	@Test
	void aPortraitTooSmallOrOfAnotherRatioIsRefusedWithItsReasonAndSavesNothing() {
		var small = refusal("Robin", opaquePng(320, 400));
		var square = refusal("Robin", opaquePng(800, 800));
		stored();

		assertThat(small.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_TOO_SMALL);
		assertThat(square.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_WRONG_RATIO);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM character_version", Long.class)).isZero();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aRemovedPortraitIsDeletedAndADiscardedDraftTakesItsPortraitWithIt() {
		var removed = this.service.create(EDITOR, CHOPPER, character("Robin"),
				ImageChange.replaceWith(opaquePng(800, 1000)));
		stored();
		this.service.edit(EDITOR, CHOPPER, removed.id(), 1, character("Robin"), ImageChange.REMOVE);
		stored();
		assertThat(portraitOf(removed.id())).isNull();
		assertThat(this.imageRepository.count()).isZero();

		var discarded = this.service.create(EDITOR, CHOPPER, character("Nami"),
				ImageChange.replaceWith(opaquePng(800, 1000)));
		stored();
		this.service.delete(EDITOR, CHOPPER, discarded.id(), 1);
		stored();
		assertThat(this.imageRepository.count()).isZero();
	}

	@Test
	void aPortraitIsSeenOnlyByWhoeverSeesAVersionUsingIt() {
		var content = this.service.create(EDITOR, CHOPPER, character("Robin"),
				ImageChange.replaceWith(opaquePng(800, 1000)));
		stored();
		String portraitId = portraitOf(content.id());

		assertThat(this.imageService.get(EDITOR, portraitId).id()).isEqualTo(portraitId);
		assertThatThrownBy(() -> this.imageService.get(READER, portraitId)).isInstanceOf(ImageNotFoundException.class);
	}

	private ImageRefusedException refusal(String romaji, byte[] upload) {
		return catchThrowableOfType(ImageRefusedException.class,
				() -> this.service.create(EDITOR, CHOPPER, character(romaji), ImageChange.replaceWith(upload)));
	}

	private Character character(String romaji) {
		return this.fixtures.complete(romaji);
	}

	private String portraitOf(UUID contentId) {
		return this.service.getVersion(EDITOR, CHOPPER, contentId, 1).body().imageId();
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
