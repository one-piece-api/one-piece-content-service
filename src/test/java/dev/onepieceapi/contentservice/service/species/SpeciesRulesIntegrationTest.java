package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
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

import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.service.species.SpeciesFixtures.ADMIN;
import static dev.onepieceapi.contentservice.service.species.SpeciesFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.species.SpeciesFixtures.PUBLISHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What a species needs to go online (implementation plan of the Species, S1), against a
 * real PostgreSQL (Testcontainers): a first appearance, and its chapter and episode
 * online. Whoever tries anyway is refused with the reason, even an administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class SpeciesRulesIntegrationTest {

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
	private TestEntityManager entityManager;

	private SpeciesFixtures fixtures;

	private SpeciesService service;

	@BeforeEach
	void setUp() {
		this.fixtures = new SpeciesFixtures(this.contentRepository, this.speciesRepository, this.chapterRepository,
				this.episodeRepository, this.contentVersionRepository, this.languageRepository,
				this.auditLogRepository);
		this.service = this.fixtures.service();
	}

	@Test
	void anSpeciesGoesOnlineWhileItsChapterIsAndTheSlugOfItsRomajiIsKept() {
		UUID species = this.fixtures.species(READY_TO_PUBLISH, this.fixtures.complete("Gyojin"));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, species, 1).version().status()).isEqualTo(PUBLISHED);
		stored();
		assertThat(this.speciesRepository.slugIsTakenByAnother("gyojin", UUID.randomUUID())).isTrue();
	}

	@Test
	void anSpeciesWithoutAFirstAppearanceDoesNotGoOnlineEvenForAnAdministrator() {
		UUID species = this.fixtures.species(READY_TO_PUBLISH, this.fixtures.complete("Kaigun", StoryPoint.NONE));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, species, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.FIRST_APPEARANCE_MISSING);
	}

	@Test
	void anSpeciesDoesNotGoOnlineWhileItsChapterIsNot() {
		UUID chapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, ARCHIVED);
		UUID species = this.fixtures.species(READY_TO_PUBLISH,
				this.fixtures.complete("Kaigun", StoryPoint.ofChapter(chapter)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(PUBLISHER, CHOPPER, species, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("firstAppearanceChapterId", chapter, "firstAppearanceChapterNumber", 1));
	}

	@Test
	void anSpeciesFirstAppearingInAnOnlineEpisodeAloneGoesOnline() {
		StoryPoint episode = OnlineChapters.onlineEpisode(this.contentRepository, this.episodeRepository);
		UUID species = this.fixtures.species(READY_TO_PUBLISH, this.fixtures.complete("Kaigun", episode));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, species, 1).version().status()).isEqualTo(PUBLISHED);
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
