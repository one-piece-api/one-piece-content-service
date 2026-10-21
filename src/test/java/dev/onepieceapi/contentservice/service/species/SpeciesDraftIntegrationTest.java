package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.exception.SlugAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import org.hibernate.exception.ConstraintViolationException;
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

import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.service.species.SpeciesFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.species.SpeciesFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.species.SpeciesFixtures.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Creating a species and editing its draft against a real PostgreSQL (Testcontainers) -
 * the same workflow as every entity, covered with the Devil Fruit Type - and what is its
 * own (implementation plan of the Species, S1): the romaji as key and its slug, the names
 * unique per language, the first appearance, and what the schema guarantees whatever the
 * service does.
 * <p>
 * Seeded for every test: Gyojin online, complete in both languages.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class SpeciesDraftIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private SpeciesVersionRepository speciesRepository;

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
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entityManager;

	private SpeciesFixtures fixtures;

	private SpeciesService service;

	@BeforeEach
	void setUp() {
		this.fixtures = new SpeciesFixtures(this.contentRepository, this.speciesRepository, this.chapterRepository,
				this.episodeRepository, this.contentVersionRepository, this.languageRepository,
				this.auditLogRepository);
		this.service = this.fixtures.service();
		this.fixtures.species(PUBLISHED, this.fixtures.complete("Gyojin"));
		stored();
	}

	@Test
	void aNewSpeciesIsBornWithADraftSayingWhatWasWrittenTheSpaceAroundItIgnored() {
		var written = new Species("  Kaigun ", Map.of("it", new SpeciesTranslation(" Marina ", null)), null,
				StoryPoint.NONE);

		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		var version = this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version();
		assertThat(version.status()).isEqualTo(DRAFT);
		assertThat(version.body().romaji()).isEqualTo("Kaigun");
		assertThat(version.body().translations()).containsOnlyKeys("it");
		assertThat(version.body().translationIn("it").name()).isEqualTo("Marina");
	}

	@Test
	void theDatabaseWritesTheSlugOfTheRomaji() {
		assertThat(this.jdbc.queryForObject("select romaji_slug from species_version where romaji = 'Gyojin'",
				String.class))
			.isEqualTo("gyojin");
	}

	@Test
	void aRomajiOrANameAnotherSpeciesHasIsRefusedNamingEachFieldWhateverTheCase() {
		var written = new Species(" gyojin ", Map.of("it", new SpeciesTranslation("GYOJIN (IT)", null)), null,
				StoryPoint.NONE);

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("romaji", "translations[it].name");
	}

	@Test
	void theSameNameInAnotherLanguageIsNoClash() {
		// "Gyojin (it)" is taken in Italian, not in English.
		var written = new Species("Kaigun", Map.of("en", new SpeciesTranslation("Gyojin (it)", null)), null,
				StoryPoint.NONE);

		assertThat(this.service.create(EDITOR, CHOPPER, written).versions()).hasSize(1);
	}

	@Test
	void aRomajiGivingTheSlugOfAnotherSpeciesIsRefused() {
		// No equal text, but the same address: gyojin.
		var refused = catchThrowableOfType(SlugAlreadyUsedException.class,
				() -> this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Gyojin!")));

		assertThat(fieldsOf(refused)).containsExactly("romaji");
	}

	@Test
	void aChapterNothingApprovedOfCannotBeTheFirstAppearance() {
		UUID draftChapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, DRAFT);
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.service.create(EDITOR, CHOPPER,
				this.fixtures.complete("Kaigun", StoryPoint.ofChapter(draftChapter))));

		assertThat(fieldsOf(refused)).containsExactly("firstAppearance.chapter");
	}

	@Test
	void aDraftMayBeSavedIncompleteButNotSentToReview() {
		var content = this.service.create(EDITOR, CHOPPER,
				new Species("Kaigun", Map.of("it", new SpeciesTranslation("Marina", null)), null, StoryPoint.NONE));
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, CHOPPER, content.id(), 1));

		assertThat(fieldsOf(refused)).containsExactly("firstAppearance", "translations[en].name",
				"translations[en].description", "translations[it].description");
	}

	@Test
	void aCompleteDraftIsSentToReview() {
		var content = this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Kaigun"));
		stored();

		assertThat(this.service.submit(EDITOR, CHOPPER, content.id(), 1).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
	}

	@Test
	void aLanguageAnSpeciesSaysSomethingInIsInUse() {
		assertThat(this.speciesRepository.existsByLanguage("it")).isTrue();
		assertThat(this.speciesRepository.existsByLanguage("fr")).isFalse();
	}

	@Test
	void theDatabaseRefusesAFirstAppearanceThatIsNoChapter() {
		UUID episode = OnlineChapters.episode(this.contentRepository, this.episodeRepository, PUBLISHED);
		this.fixtures.species(DRAFT, this.fixtures.complete("Kaigun", StoryPoint.ofChapter(episode)));

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_species_version_first_appearance");
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
