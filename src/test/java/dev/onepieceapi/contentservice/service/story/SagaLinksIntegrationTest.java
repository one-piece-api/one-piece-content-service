package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
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
import java.time.Duration;
import java.time.Instant;
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
 * What the arcs say of their sagas, read against a real PostgreSQL (Testcontainers): the
 * sagas an arc may be linked to, by number and narrowed by what the number or a title
 * contains, and the saga an arc points to as it is today (implementation plan of the
 * story structure, S4, S8).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class SagaLinksIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private SagaVersionRepository sagaRepository;

	@Autowired
	private TestEntityManager entityManager;

	private SagaLinks links;

	private UUID eastBlue;

	private UUID alabasta;

	private UUID skypiea;

	private UUID waterSeven;

	@BeforeEach
	void setUp() {
		this.links = new SagaLinks(this.sagaRepository, Clock.fixed(NOW, ZoneOffset.UTC));

		// Numbers with gaps, and 2 before 10 whatever the order of the texts.
		this.alabasta = sagaContent(10, "Alabasta", PUBLISHED);
		this.eastBlue = sagaContent(2, "East Blue", READY_TO_PUBLISH);
		this.skypiea = sagaContent(12, "Skypiea", ARCHIVED);
		this.waterSeven = sagaContent(30, "Water Seven", DRAFT);
		sagaContent(40, "Marineford", IN_REVIEW);
	}

	@Test
	void onlyTheSagasWithAnApprovedVersionCanBeLinkedInTheOrderOfTheirNumberAndTheirTitlesAreThoseApproved() {
		sagaVersion(this.alabasta, 2, DRAFT, 10, "Alabasta Revised");
		stored();

		var page = this.links.linkable(null, 0, 10);

		assertThat(page.getContent()).extracting(NumberedReference::number).containsExactly(2, 10, 12);
		assertThat(page.getContent().get(1).names()).containsExactly(entry("it", "Alabasta"));
		assertThat(page.getContent().getFirst().id()).isEqualTo(this.eastBlue);
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void theLinkableSagasAreNarrowedByWhatTheirNumberOrATitleContainsAndPaged() {
		stored();

		assertThat(this.links.linkable("blue", 0, 10).getContent()).extracting(NumberedReference::number)
			.containsExactly(2);
		assertThat(this.links.linkable("SKY", 0, 10).getContent()).extracting(NumberedReference::number)
			.containsExactly(12);
		assertThat(this.links.linkable("12", 0, 10).getContent()).extracting(NumberedReference::number)
			.containsExactly(12);
		assertThat(this.links.linkable("1", 0, 10).getContent()).extracting(NumberedReference::number)
			.containsExactly(10, 12);
		var second = this.links.linkable(null, 1, 2);
		assertThat(second.getContent()).extracting(NumberedReference::number).containsExactly(12);
		assertThat(second.getTotalPages()).isEqualTo(2);
		assertThat(this.links.linkable("marineford", 0, 10).getContent()).isEmpty();
		assertThat(this.links.linkable("30", 0, 10).getContent()).isEmpty();
	}

	@Test
	void theSagaAnArcPointsToIsAsItWasLastApprovedNotAsItIsWritten() {
		sagaVersion(this.alabasta, 2, DRAFT, 11, "Alabasta Draft");
		stored();

		Map<UUID, NumberedReference> references = this.links
			.referencesOf(List.of(this.alabasta, this.eastBlue, this.waterSeven));

		assertThat(references).containsOnlyKeys(this.alabasta, this.eastBlue);
		assertThat(references.get(this.alabasta))
			.isEqualTo(new NumberedReference(this.alabasta, 10, Map.of("it", "Alabasta")));
	}

	@Test
	void theSagasOfSomeArcsAreLookedUpOnceAndNoSagaYetIsLeftOut() {
		stored();
		var arcs = List.of(new Arc(1, this.alabasta, Map.of()), new Arc(2, this.alabasta, Map.of()),
				new Arc(3, null, Map.of()), new Arc(4, this.eastBlue, Map.of()));

		assertThat(this.links.referencesFor(arcs)).containsOnlyKeys(this.alabasta, this.eastBlue);
		assertThat(this.links.referencesOf(List.of())).isEmpty();
	}

	private UUID sagaContent(int number, String title, VersionStatus status) {
		UUID contentId = this.entityManager.persist(new ContentEntity(UUID.randomUUID(), EntityType.SAGA, EARLIER))
			.getId();
		sagaVersion(contentId, 1, status, number, title);
		return contentId;
	}

	private void sagaVersion(UUID contentId, int versionNumber, VersionStatus status, int number, String title) {
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(versionNumber)
			.basedOnNumber(versionNumber == 1 ? null : versionNumber - 1)
			.author(UserMapper.toEmbeddable(this.chopper))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new SagaVersionEntity(workflow);
		StoryVersionMapper.rewrite(version, new Saga(number, Map.of("it", new StoryTranslation(title, "Descrizione"))),
				EARLIER);
		this.sagaRepository.save(version);
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
