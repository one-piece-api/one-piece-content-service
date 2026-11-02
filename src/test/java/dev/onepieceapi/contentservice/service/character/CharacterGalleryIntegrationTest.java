package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.image.GalleryUploads;
import dev.onepieceapi.contentservice.domain.readertime.Galleried;
import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
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
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.content.StoredImages;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.image.ImageNormalizer;
import dev.onepieceapi.contentservice.service.image.ImageService;
import dev.onepieceapi.contentservice.service.validation.ImageValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.readertime.ImageKind;
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
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.READER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.fieldsOf;
import static dev.onepieceapi.contentservice.service.image.TestImages.ENCODER;
import static dev.onepieceapi.contentservice.service.image.TestImages.drawn;
import static dev.onepieceapi.contentservice.service.image.TestImages.opaquePng;
import static dev.onepieceapi.contentservice.service.image.TestImages.png;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The gallery of a character against a real PostgreSQL (Testcontainers; workflow 3.5.1,
 * reveals plan R5): uploads stored as WebP of the 4:5 canvas, in order; at most 20
 * images; an appearance discovered at the first appearance and every caption required for
 * review; images kept by id only when the draft shows them; and an image no version shows
 * any more deleted.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterGalleryIntegrationTest {

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
	private BountyVersionRepository bountyRepository;

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

	private CharacterFixtures fixtures;

	private CharacterService service;

	private ImageService imageService;

	private String portrait;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(CharacterFixtures.NOW, ZoneOffset.UTC);
		var store = new JpaImageStore(this.imageRepository, clock);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(ENCODER), store, PORTRAIT_IMAGES);
		this.portrait = StoredImages.portrait(this.imageRepository);
		this.fixtures = CharacterFixtures.builder()
			.contents(this.contentRepository)
			.characters(this.characterRepository)
			.species(this.speciesRepository)
			.bounties(this.bountyRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.firstAppearance(OnlineChapters.onlineChapter(this.contentRepository, this.chapterRepository))
			.portrait(this.portrait)
			.build();
		this.service = this.fixtures.service(images);
		this.imageService = new ImageService(store, this.devilFruitRepository, this.volumeRepository,
				this.bountyRepository, this.affiliationRepository, this.speciesRepository, this.characterRepository);
	}

	@Test
	void luffyBeforeAndAfterTheTimeskipAndGearFiveAreStoredInOrderAndSentToReview() {
		var content = this.service.create(EDITOR, CHOPPER,
				luffy(appearance("Prima"), appearance("Dopo"), form("Gear 5")),
				uploads(colored(Color.BLUE), colored(Color.GREEN), colored(Color.YELLOW)));
		stored();

		var gallery = galleryOf(content.id(), 1);
		assertThat(gallery).extracting(GalleryImage::kind)
			.containsExactly(ImageKind.ASPETTO, ImageKind.ASPETTO, ImageKind.FORMA);
		assertThat(gallery).extracting(image -> image.captionIn("it")).containsExactly("Prima", "Dopo", "Gear 5");
		assertThat(gallery).extracting(GalleryImage::imageId).doesNotContainNull().doesNotHaveDuplicates();
		var image = this.imageService.get(EDITOR, gallery.getFirst().imageId());
		assertThat(image.contentType()).isEqualTo("image/webp");
		assertThat(image.width()).isEqualTo(320);
		assertThat(image.height()).isEqualTo(400);

		this.service.submit(EDITOR, CHOPPER, content.id(), 1);
	}

	@Test
	void aGalleryOfMoreThanTwentyImagesIsRefused() {
		var crowded = luffy(
				Collections.nCopies(Galleried.MAX_IMAGES + 1, appearance("Aspetto")).toArray(GalleryImage[]::new));
		var files = Collections.nCopies(Galleried.MAX_IMAGES + 1, colored(Color.BLUE)).toArray(byte[][]::new);

		var refused = catchThrowableOfType(DomainException.class,
				() -> this.service.create(EDITOR, CHOPPER, crowded, uploads(files)));

		assertThat(fieldsOf(refused)).contains(Galleried.GALLERY_FIELD);
	}

	@Test
	void noAppearanceDiscoveredAtTheFirstAppearanceOrACaptionMissingKeepsTheDraftFromReview() {
		var onlyForm = this.service.create(EDITOR, CHOPPER, luffy(form("Gear 5")), uploads(colored(Color.BLUE)));
		var italianOnly = new GalleryImage(null, ImageKind.ASPETTO, this.fixtures.fromStart(), Map.of("it", "Aspetto"));
		var uncaptioned = this.service.create(EDITOR, CHOPPER, character("Zoro").withGallery(List.of(italianOnly)),
				uploads(colored(Color.GREEN)));
		stored();

		var noAppearance = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, CHOPPER, onlyForm.id(), 1));
		var noCaption = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, CHOPPER, uncaptioned.id(), 1));

		assertThat(fieldsOf(noAppearance)).containsExactly(Galleried.GALLERY_FIELD);
		assertThat(fieldsOf(noCaption)).containsExactly("gallery[0].caption[en]");
	}

	@Test
	void editsKeepTheImagesNamedAndDeleteTheOnesRemoved() {
		var content = this.service.create(EDITOR, CHOPPER, luffy(appearance("Prima"), form("Gear 5")),
				uploads(colored(Color.BLUE), colored(Color.GREEN)));
		stored();
		var before = galleryOf(content.id(), 1);

		this.service.edit(EDITOR, CHOPPER, content.id(), 1, luffy(before.getFirst()));
		stored();

		assertThat(galleryOf(content.id(), 1)).containsExactly(before.getFirst());
		assertThat(this.imageRepository.existsById(before.get(1).imageId())).isFalse();
		assertThat(this.imageRepository.count()).isEqualTo(2);
	}

	@Test
	void anImageAnotherVersionShowsOutlivesTheDraftThatDropsItAndADiscardedDraftTakesItsUploads() {
		UUID robin = this.fixtures.character(VersionStatus.PUBLISHED, character("Robin"));
		stored();
		this.service.openNewVersion(EDITOR, CHOPPER, robin, 1);
		stored();
		assertThat(galleryOf(robin, 2)).extracting(GalleryImage::imageId).containsExactly(this.portrait);

		this.service.edit(EDITOR, CHOPPER, robin, 2, character("Robin").withGallery(List.of(appearance("Dopo"))),
				uploads(colored(Color.BLUE)));
		stored();
		assertThat(this.imageRepository.existsById(this.portrait)).isTrue();
		assertThat(this.imageRepository.count()).isEqualTo(2);

		this.service.delete(EDITOR, CHOPPER, robin, 2);
		stored();
		assertThat(this.imageRepository.count()).isEqualTo(1);
	}

	@Test
	void anImageTheDraftDoesNotShowCannotBeNamedById() {
		var nami = this.service.create(EDITOR, CHOPPER, luffy(appearance("Aspetto")), uploads(colored(Color.BLUE)));
		stored();
		var namiImage = galleryOf(nami.id(), 1).getFirst();
		var zoro = this.service.create(EDITOR, CHOPPER, character("Zoro").withGallery(List.of(appearance("Aspetto"))),
				uploads(colored(Color.GREEN)));
		stored();
		var stolen = character("Zoro").withGallery(List.of(namiImage));

		var onCreate = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, stolen, GalleryUploads.NONE));
		var onEdit = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.edit(EDITOR, CHOPPER, zoro.id(), 1, stolen));

		assertThat(fieldsOf(onCreate)).containsExactly("gallery[0].imageId");
		assertThat(fieldsOf(onEdit)).containsExactly("gallery[0].imageId");
	}

	@Test
	void anUploadTooSmallOrOfAnotherRatioIsRefusedAtItsEntryAndSavesNoVersion() {
		var small = catchThrowableOfType(ImageRefusedException.class, () -> this.service.create(EDITOR, CHOPPER,
				luffy(appearance("Prima"), form("Gear 5")), uploads(colored(Color.BLUE), opaquePng(320, 400))));
		var square = catchThrowableOfType(ImageRefusedException.class,
				() -> this.service.create(EDITOR, CHOPPER, luffy(appearance("Prima")), uploads(opaquePng(800, 800))));
		stored();

		assertThat(small.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_TOO_SMALL);
		assertThat(small.getDetails()).containsEntry("field", "gallery[1].image");
		assertThat(square.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_WRONG_RATIO);
		assertThat(square.getDetails()).containsEntry("field", "gallery[0].image");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM character_version", Long.class)).isZero();
	}

	@Test
	void anImageIsSeenOnlyByWhoeverSeesAVersionShowingIt() {
		var content = this.service.create(EDITOR, CHOPPER, luffy(appearance("Aspetto")), uploads(colored(Color.BLUE)));
		stored();
		String imageId = galleryOf(content.id(), 1).getFirst().imageId();

		assertThat(this.imageService.get(EDITOR, imageId).id()).isEqualTo(imageId);
		assertThatThrownBy(() -> this.imageService.get(READER, imageId)).isInstanceOf(ImageNotFoundException.class);
	}

	/** Complete, showing these images instead of the seeded portrait. */
	private Character luffy(GalleryImage... gallery) {
		return character("Luffy").withGallery(List.of(gallery));
	}

	/** An appearance from the first appearance on, still to be uploaded. */
	private GalleryImage appearance(String caption) {
		return new GalleryImage(null, ImageKind.ASPETTO, this.fixtures.fromStart(),
				Map.of("it", caption, "en", caption));
	}

	/** A form from the first appearance on, still to be uploaded. */
	private GalleryImage form(String caption) {
		return new GalleryImage(null, ImageKind.FORMA, this.fixtures.fromStart(), Map.of("it", caption, "en", caption));
	}

	/** The files, each for the gallery entry at its own index. */
	private static GalleryUploads uploads(byte[]... files) {
		return new GalleryUploads(IntStream.range(0, files.length)
			.boxed()
			.collect(Collectors.toMap(Function.identity(), index -> files[index])));
	}

	/** A 4:5 picture larger than the canvas; another background makes another image. */
	private static byte[] colored(Color background) {
		return png(drawn(800, 1000, background));
	}

	private Character character(String romaji) {
		return this.fixtures.complete(romaji);
	}

	private List<GalleryImage> galleryOf(UUID contentId, int versionNumber) {
		return this.service.getVersion(EDITOR, CHOPPER, contentId, versionNumber).version().body().gallery();
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
