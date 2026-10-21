package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
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
 * The characters a bounty may be linked to, and the one it points to, read against a real
 * PostgreSQL (Testcontainers) (implementation plan of the Character, C6).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterLinksIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private CharacterVersionRepository characterRepository;

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
	private LanguageRepository languageRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TestEntityManager entityManager;

	private CharacterFixtures fixtures;

	private CharacterLinks links;

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
		this.links = new CharacterLinks(this.characterRepository, Clock.fixed(CharacterFixtures.NOW, ZoneOffset.UTC));
	}

	@Test
	void onlyTheCharactersWithAnApprovedVersionCanBeLinkedByRomajiAndTheirNamesAreThoseApproved() {
		UUID nami = character("Nami", PUBLISHED);
		character("Robin", READY_TO_PUBLISH);
		character("Franky", ARCHIVED);
		character("Draft", DRAFT);
		character("Review", IN_REVIEW);
		stored();

		var page = this.links.linkable(null, 0, 10);

		assertThat(page.getContent()).extracting(CharacterReference::romaji).containsExactly("Franky", "Nami", "Robin");
		assertThat(page.getContent().get(1).id()).isEqualTo(nami);
		assertThat(page.getContent().get(1).names()).containsExactly(entry("en", "Nami (en)"),
				entry("it", "Nami (it)"));
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void theLinkableCharactersAreNarrowedByWhatTheirRomajiOrANameContainsAndPaged() {
		character("Nami", PUBLISHED);
		character("Robin", PUBLISHED);
		character("Zoro", PUBLISHED);
		stored();

		assertThat(this.links.linkable("ZORO", 0, 10).getContent()).extracting(CharacterReference::romaji)
			.containsExactly("Zoro");
		assertThat(this.links.linkable("(it)", 0, 2).getContent()).extracting(CharacterReference::romaji)
			.containsExactly("Nami", "Robin");
		var second = this.links.linkable(null, 1, 2);
		assertThat(second.getContent()).extracting(CharacterReference::romaji).containsExactly("Zoro");
		assertThat(second.getTotalPages()).isEqualTo(2);
		assertThat(this.links.linkable("sanji", 0, 10).getContent()).isEmpty();
	}

	@Test
	void anIdNamingNothingAndACharacterNeverApprovedAreToldAlike() {
		UUID approved = character("Nami", PUBLISHED);
		UUID draft = character("Robin", DRAFT);
		stored();

		Map<UUID, CharacterReference> references = this.links.referencesOf(List.of(approved, draft, UUID.randomUUID()));

		assertThat(references).containsOnlyKeys(approved);
		assertThat(references.get(approved))
			.isEqualTo(new CharacterReference(approved, "Nami", Map.of("it", "Nami (it)", "en", "Nami (en)")));
		assertThat(this.links.referencesOf(List.of())).isEmpty();
	}

	private UUID character(String romaji, VersionStatus status) {
		return this.fixtures.character(status, this.fixtures.complete(romaji));
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
