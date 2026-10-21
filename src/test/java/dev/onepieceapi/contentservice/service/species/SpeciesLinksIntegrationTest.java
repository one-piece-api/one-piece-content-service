package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
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

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The species a character may be linked to, and the ones it points to, read against a
 * real PostgreSQL (Testcontainers) (implementation plan of the Species, D7).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class SpeciesLinksIntegrationTest {

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

	private SpeciesLinks links;

	@BeforeEach
	void setUp() {
		this.fixtures = new SpeciesFixtures(this.contentRepository, this.speciesRepository, this.chapterRepository,
				this.episodeRepository, this.contentVersionRepository, this.languageRepository,
				this.auditLogRepository);
		this.links = new SpeciesLinks(this.speciesRepository, Clock.fixed(SpeciesFixtures.NOW, ZoneOffset.UTC));
	}

	@Test
	void onlyTheSpeciesWithAnApprovedVersionCanBeLinkedByRomajiAndTheirNamesAreThoseApproved() {
		UUID gyojin = species("Gyojin", PUBLISHED);
		species("Mink", READY_TO_PUBLISH);
		species("Kyojin", ARCHIVED);
		species("Draft", DRAFT);
		species("Review", IN_REVIEW);
		stored();

		var page = this.links.linkable(null, 0, 10);

		assertThat(page.getContent()).extracting(SpeciesReference::romaji).containsExactly("Gyojin", "Kyojin", "Mink");
		assertThat(page.getContent().getFirst().id()).isEqualTo(gyojin);
		assertThat(page.getContent().getFirst().names()).containsExactly(entry("en", "Gyojin (en)"),
				entry("it", "Gyojin (it)"));
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void theLinkableSpeciesAreNarrowedByWhatTheirRomajiOrANameContainsAndPaged() {
		species("Gyojin", PUBLISHED);
		species("Kyojin", PUBLISHED);
		species("Mink", PUBLISHED);
		stored();

		assertThat(this.links.linkable("MINK", 0, 10).getContent()).extracting(SpeciesReference::romaji)
			.containsExactly("Mink");
		assertThat(this.links.linkable("yojin (it)", 0, 10).getContent()).extracting(SpeciesReference::romaji)
			.containsExactly("Gyojin", "Kyojin");
		var second = this.links.linkable(null, 1, 2);
		assertThat(second.getContent()).extracting(SpeciesReference::romaji).containsExactly("Mink");
		assertThat(second.getTotalPages()).isEqualTo(2);
		assertThat(this.links.linkable("human", 0, 10).getContent()).isEmpty();
	}

	@Test
	void anIdNamingNothingAndASpeciesNeverApprovedAreToldAlike() {
		UUID approved = species("Gyojin", PUBLISHED);
		UUID draft = species("Mink", DRAFT);
		stored();

		Map<UUID, SpeciesReference> references = this.links.referencesOf(List.of(approved, draft, UUID.randomUUID()));

		assertThat(references).containsOnlyKeys(approved);
		assertThat(references.get(approved))
			.isEqualTo(new SpeciesReference(approved, "Gyojin", Map.of("it", "Gyojin (it)", "en", "Gyojin (en)")));
		assertThat(this.links.referencesOf(List.of())).isEmpty();
	}

	private UUID species(String romaji, VersionStatus status) {
		Species body = this.fixtures.complete(romaji);
		return this.fixtures.species(status, body);
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
