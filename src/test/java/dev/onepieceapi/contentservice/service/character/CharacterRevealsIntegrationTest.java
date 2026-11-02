package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterField;
import dev.onepieceapi.contentservice.domain.character.CharacterKnowledge;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ImageRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.StoredImages;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.exception.SlugAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.PUBLISHER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.violationsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The reveals of a Character (workflow 3.7.1; reveals plan R4), against a real PostgreSQL
 * (Testcontainers): an identity learned later, with the slug of every romaji, and a
 * status learned later.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterRevealsIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private CharacterVersionRepository characterRepository;

	@Autowired
	private AffiliationVersionRepository affiliationRepository;

	@Autowired
	private SpeciesVersionRepository speciesRepository;

	@Autowired
	private BountyVersionRepository bountyRepository;

	@Autowired
	private ChapterVersionRepository chapterRepository;

	@Autowired
	private EpisodeVersionRepository episodeRepository;

	@Autowired
	private ContentVersionRepository contentVersionRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TestEntityManager entityManager;

	private CharacterFixtures fixtures;

	private CharacterService service;

	private StoryPoint chapter43;

	@BeforeEach
	void setUp() {
		this.chapter43 = chapter(43);
		this.fixtures = CharacterFixtures.builder()
			.contents(this.contentRepository)
			.characters(this.characterRepository)
			.species(this.speciesRepository)
			.affiliations(this.affiliationRepository)
			.bounties(this.bountyRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.firstAppearance(this.chapter43)
			.portrait(StoredImages.portrait(this.imageRepository))
			.build();
		this.service = this.fixtures.service();
		stored();
	}

	@Test
	void aceIsAliveUntilMarinefordAndReadsBackAsSaved() {
		// Ace dies in chapter 574 (One Piece Wiki, Portgas D. Ace); his first appearance
		// is the fixtures' chapter.
		StoryPoint dies = chapter(574);
		var ace = this.fixtures.complete("Portgas D. Ace")
			.toBuilder()
			.status(CharacterStatus.DECEASED)
			.initialKnowledge(new CharacterKnowledge(null, null, CharacterStatus.ALIVE, Map.of()))
			.reveals(List.of(new Reveal<>(dies, Set.of(CharacterField.STATUS), CharacterKnowledge.NONE,
					Map.of("it", "Marineford", "en", "Marineford"))))
			.build();

		var created = this.service.create(EDITOR, CHOPPER, ace);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, created.id(), 1).version().body())
			.isEqualTo(ace.normalized());
	}

	@Test
	void sanjiIsLearnedToBeAVinsmokeAndBothRomajiTakeASlugOnline() {
		// Illustrative chapter: the point of the reveal is not under test.
		UUID sanji = this.fixtures.character(READY_TO_PUBLISH, sanji());
		stored();

		this.service.publish(PUBLISHER, CHOPPER, sanji, 1);
		stored();

		assertThat(this.characterRepository.slugOf("Sanji")).isPresent();
		assertThat(this.characterRepository.slugOf("Vinsmoke Sanji")).isPresent();
		var refused = catchThrowableOfType(SlugAlreadyUsedException.class,
				() -> this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Sanji")));
		assertThat(violationsOf(refused)).extracting(FieldViolation::field).containsExactly("romaji");
	}

	@Test
	void theInitialIdentityNeedsARomajiAndHoldsOnlyWhatSomeRevealNames() {
		var written = sanji().toBuilder()
			.initialKnowledge(new CharacterKnowledge(null, null, CharacterStatus.ALIVE, Map.of("it", "Sanji")))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(violationsOf(refused)).extracting(FieldViolation::field, FieldViolation::message)
			.containsExactlyInAnyOrder(
					tuple("initialKnowledge.romaji", "must be known at the first appearance: a reveal names identity"),
					tuple("initialKnowledge.status", "must be left out: no reveal names status"));
	}

	private Character sanji() {
		return this.fixtures.complete("Vinsmoke Sanji")
			.toBuilder()
			.initialKnowledge(new CharacterKnowledge("Sanji", null, null, Map.of("it", "Sanji", "en", "Sanji")))
			.reveals(List.of(new Reveal<>(chapter(800), Set.of(CharacterField.IDENTITY), CharacterKnowledge.NONE,
					Map.of("it", "Il nome di famiglia", "en", "The family name"))))
			.build();
	}

	private StoryPoint chapter(int number) {
		return StoryPoint
			.ofChapter(OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED, number));
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
