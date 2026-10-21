package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
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
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.ADMIN;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.PUBLISHER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.RETIRER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What a character needs to go online and what it keeps online (implementation plan of
 * the Character, D8, D9), against a real PostgreSQL (Testcontainers): a first appearance
 * online and every species of its list online; and no species is retired while an online
 * character names it. Whoever tries anyway is refused with the reason, even an
 * administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterRulesIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private CharacterVersionRepository characterRepository;

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
	private TestEntityManager entityManager;

	private CharacterFixtures fixtures;

	private CharacterService service;

	@BeforeEach
	void setUp() {
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
		this.service = this.fixtures.service();
	}

	@Test
	void aCharacterGoesOnlineWhileItsChapterAndEverySpeciesAreAndTheSlugOfItsRomajiIsKept() {
		UUID ningen = this.fixtures.species(PUBLISHED, "Ningen");
		UUID nami = this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Nami").toBuilder().speciesIds(List.of(ningen)).build());
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, nami, 1).version().status()).isEqualTo(PUBLISHED);
		stored();
		assertThat(this.characterRepository.slugIsTakenByAnother("nami", UUID.randomUUID())).isTrue();
	}

	@Test
	void aCharacterWithoutAFirstAppearanceDoesNotGoOnlineEvenForAnAdministrator() {
		UUID nami = this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Nami").toBuilder().firstAppearance(StoryPoint.NONE).build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, nami, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.FIRST_APPEARANCE_MISSING);
	}

	@Test
	void aCharacterDoesNotGoOnlineWhileItsChapterIsNot() {
		UUID chapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, ARCHIVED);
		UUID nami = this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Nami").toBuilder().firstAppearance(StoryPoint.ofChapter(chapter)).build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(PUBLISHER, CHOPPER, nami, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("firstAppearanceChapterId", chapter, "firstAppearanceChapterNumber", 1));
	}

	@Test
	void aCharacterDoesNotGoOnlineWhileASpeciesOfItsListIsNotNamingItsIndexEvenForAnAdministrator() {
		UUID ningen = this.fixtures.species(PUBLISHED, "Ningen");
		UUID mink = this.fixtures.species(ARCHIVED, "Mink");
		UUID carrot = this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Carrot").toBuilder().speciesIds(List.of(ningen, mink)).build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, carrot, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("speciesIndex", 1, "speciesId", mink, "speciesRomaji", "Mink"));
	}

	@Test
	void aSpeciesIsNotRetiredWhileOnlineCharactersNameItEvenForAnAdministrator() {
		UUID ningen = this.fixtures.species(PUBLISHED, "Ningen");
		UUID nami = this.fixtures.character(PUBLISHED,
				this.fixtures.complete("Nami").toBuilder().speciesIds(List.of(ningen)).build());
		this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Robin").toBuilder().speciesIds(List.of(ningen)).build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fixtures.speciesService().retire(ADMIN, CHOPPER, ningen, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "characters", List.of(Map.of("id", nami, "romaji", "Nami"))));
	}

	@Test
	void aSpeciesNoOnlineCharacterNamesIsRetired() {
		UUID gyojin = this.fixtures.species(PUBLISHED, "Gyojin");
		this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Arlong").toBuilder().speciesIds(List.of(gyojin)).build());
		stored();

		assertThat(this.fixtures.speciesService().retire(RETIRER, CHOPPER, gyojin, 1).version().status())
			.isEqualTo(RETIRED);
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
