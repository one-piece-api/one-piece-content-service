package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Creating a character and editing its draft against a real PostgreSQL (Testcontainers) -
 * the workflow is the one of every entity - and what is its own (implementation plan of
 * the Character, C1): the romaji as key and its slug, the names unique per language, the
 * lists kept in their order, the species linked, and what the schema guarantees whatever
 * the service does.
 * <p>
 * Seeded for every test: Nami online, complete in both languages; Ningen and Gyojin
 * online species.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterDraftIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private CharacterVersionRepository characterRepository;

	@Autowired
	private BountyVersionRepository bountyRepository;

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

	private CharacterFixtures fixtures;

	private CharacterService service;

	private UUID ningen;

	private UUID gyojin;

	@BeforeEach
	void setUp() {
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
			.build();
		this.service = this.fixtures.service();
		this.fixtures.character(PUBLISHED, this.fixtures.complete("Nami"));
		this.ningen = this.fixtures.species(PUBLISHED, "Ningen");
		this.gyojin = this.fixtures.species(PUBLISHED, "Gyojin");
		stored();
	}

	@Test
	void aNewCharacterIsBornWithADraftSayingWhatWasWrittenItsListsInTheirOrder() {
		var written = this.fixtures.complete("  Arlong ")
			.toBuilder()
			.epithets(List.of(this.fixtures.epithet(Map.of("en", " Saw-Tooth ")),
					this.fixtures.epithet(Map.of("it", "Il Sega"))))
			.aliases(this.fixtures.aliases("Arlong-san", " Sawshark "))
			.species(this.fixtures.speciesEntries(this.gyojin, this.ningen))
			.build();

		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		var body = this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body();
		assertThat(body.romaji()).isEqualTo("Arlong");
		assertThat(body.epithets()).containsExactly(this.fixtures.epithet(Map.of("en", "Saw-Tooth")),
				this.fixtures.epithet(Map.of("it", "Il Sega")));
		assertThat(body.aliases()).containsExactlyElementsOf(this.fixtures.aliases("Arlong-san", "Sawshark"));
		assertThat(body.species()).containsExactlyElementsOf(this.fixtures.speciesEntries(this.gyojin, this.ningen));
	}

	@Test
	void theDatabaseWritesTheSlugOfTheRomaji() {
		assertThat(this.jdbc.queryForObject("select romaji_slug from character_version where romaji = 'Nami'",
				String.class))
			.isEqualTo("nami");
	}

	@Test
	void aRomajiOrANameAnotherCharacterHasIsRefusedNamingEachFieldWhateverTheCase() {
		var written = Character.builder()
			.romaji(" nami ")
			.translations(Map.of("it", new CharacterTranslation("NAMI (IT)")))
			.build();

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("romaji", "translations[it].name");
	}

	@Test
	void moreAliasesThanAllowedAreRefused() {
		String[] names = IntStream.rangeClosed(1, Character.MAX_ALIASES + 1)
			.mapToObj(i -> "Alias " + i)
			.toArray(String[]::new);
		var written = Character.builder().romaji("Robin").aliases(this.fixtures.aliases(names)).build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("aliases");
	}

	@Test
	void aRepeatedEpithetAliasOrSpeciesAndABlankEntryAreRefusedByTheirIndex() {
		var written = Character.builder()
			.romaji("Robin")
			.epithets(List.of(this.fixtures.epithet(Map.of("en", "Devil Child", "it", "Figlia del demonio")),
					this.fixtures.epithet(Map.of("en", "devil child")), this.fixtures.epithet(Map.of("it", " "))))
			.aliases(this.fixtures.aliases("Miss All Sunday", " ", "MISS ALL SUNDAY"))
			.species(this.fixtures.speciesEntries(this.ningen, this.ningen))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactlyInAnyOrder("epithets[1].text[en]", "epithets[2]", "aliases[1]",
				"aliases[2]", "species[1]");
	}

	@Test
	void aSpeciesNothingApprovedOfIsRefusedByItsIndex() {
		UUID draft = this.fixtures.species(DRAFT, "Mink");
		stored();
		var written = Character.builder()
			.romaji("Carrot")
			.firstAppearance(this.fixtures.fromStart().discovered())
			.species(this.fixtures.speciesEntries(this.ningen, draft))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("species[1]");
	}

	@Test
	void reorderingTheListsOfADraftKeepsTheNewOrder() {
		var first = this.fixtures.epithet(Map.of("en", "Cat Burglar"));
		var second = this.fixtures.epithet(Map.of("en", "Navigator"));
		var written = this.fixtures.complete("Robin")
			.toBuilder()
			.epithets(List.of(first, second))
			.aliases(this.fixtures.aliases("A", "B"))
			.species(this.fixtures.speciesEntries(this.ningen, this.gyojin))
			.build();
		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		this.service.edit(EDITOR, CHOPPER, content.id(), 1,
				written.toBuilder()
					.epithets(List.of(second, first))
					.aliases(this.fixtures.aliases("B", "A"))
					.species(this.fixtures.speciesEntries(this.gyojin, this.ningen))
					.build());
		stored();
		// What the commit would check: the species, swapped row by row, end up distinct.
		this.jdbc.execute("set constraints uq_character_version_species immediate");

		var body = this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body();
		assertThat(body.epithets()).containsExactly(second, first);
		assertThat(body.aliases()).containsExactlyElementsOf(this.fixtures.aliases("B", "A"));
		assertThat(body.species()).containsExactlyElementsOf(this.fixtures.speciesEntries(this.gyojin, this.ningen));
	}

	@Test
	void aNewVersionStartsFromItsBaseWithEveryListInItsOrder() {
		var body = this.fixtures.complete("Robin")
			.toBuilder()
			.epithets(List.of(this.fixtures.epithet(Map.of("en", "Devil Child")),
					this.fixtures.epithet(Map.of("en", "Light of Revolution"))))
			.aliases(this.fixtures.aliases("Miss All Sunday", "Nico Robin"))
			.species(this.fixtures.speciesEntries(this.gyojin, this.ningen))
			.build();
		UUID robin = this.fixtures.character(PUBLISHED, body);
		stored();

		this.service.openNewVersion(EDITOR, CHOPPER, robin, 1);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, robin, 2).version().body()).isEqualTo(body);
	}

	@Test
	void aDraftMayBeSavedIncompleteButNotSentToReview() {
		var content = this.service.create(EDITOR, CHOPPER,
				Character.builder()
					.romaji("Robin")
					.translations(Map.of("it", new CharacterTranslation("Robin")))
					.epithets(List.of(new Epithet(Map.of("it", "Figlia del demonio"), StoryWindow.NONE)))
					.build());
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, CHOPPER, content.id(), 1));

		assertThat(fieldsOf(refused)).containsExactly("status", "gender", "firstAppearance", "translations[en].name",
				"epithets[0].text[en]", "description", "epithets[0].discovered");
	}

	@Test
	void theDatabaseRefusesASpeciesThatIsNoSpecies() {
		UUID chapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED);
		this.fixtures.character(DRAFT,
				this.fixtures.complete("Robin").toBuilder().species(this.fixtures.speciesEntries(chapter)).build());

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_character_version_species");
	}

	@Test
	void theDatabaseRefusesTheSameSpeciesTwiceInAVersionOnceTheTransactionChecksIt() {
		this.fixtures.character(DRAFT,
				this.fixtures.complete("Robin")
					.toBuilder()
					.species(this.fixtures.speciesEntries(this.ningen, this.ningen))
					.build());
		stored();

		// Deferred to the commit, which a test rolled back never reaches: checked now
		// instead.
		assertThatThrownBy(() -> this.jdbc.execute("set constraints uq_character_version_species immediate"))
			.hasMessageContaining("uq_character_version_species");
	}

	@Test
	void aLanguageOnlyAnEpithetIsWrittenInIsInUse() {
		this.jdbc.execute("insert into language (code, name) values ('fr', 'Français')");
		this.fixtures.character(DRAFT,
				Character.builder()
					.romaji("Robin")
					.epithets(List.of(this.fixtures.epithet(Map.of("fr", "Enfant du démon"))))
					.build());
		stored();

		assertThat(this.characterRepository.existsByLanguage("it")).isTrue();
		assertThat(this.characterRepository.existsByLanguage("fr")).isTrue();
		assertThat(this.characterRepository.existsByLanguage("de")).isFalse();
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
