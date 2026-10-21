package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.CharacterVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.SpeciesVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.species.SpeciesDefinition;
import dev.onepieceapi.contentservice.service.species.SpeciesRules;
import dev.onepieceapi.contentservice.service.species.SpeciesService;
import dev.onepieceapi.contentservice.service.validation.CharacterValidator;
import dev.onepieceapi.contentservice.service.validation.SpeciesValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.Builder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * What the integration tests of the characters share: the services built as the
 * application builds them over the repositories of the test, and rows seeded straight
 * into the tables.
 */
@Builder
final class CharacterFixtures {

	static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

	static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	static final Set<Permission> READER = Set.of(Permission.CONTENT_READ);

	static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final ContentRepository contents;

	private final CharacterVersionRepository characters;

	private final SpeciesVersionRepository species;

	private final ChapterVersionRepository chapters;

	private final EpisodeVersionRepository episodes;

	private final ContentVersionRepository contentVersions;

	private final LanguageRepository languages;

	private final AuditLogRepository auditLog;

	/**
	 * The chapter online every complete character and species first appears in, seeded by
	 * the test.
	 */
	private final StoryPoint firstAppearance;

	CharacterService service() {
		return service(mock(ContentImages.class));
	}

	CharacterService service(ContentImages images) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new CharacterValidator(this.characters, this.languages, this.chapters, this.episodes,
				this.species);
		var rules = new CharacterRules(this.contents, this.chapters, this.episodes, this.species);
		return new CharacterService(new CharacterDefinition(this.characters, validator, rules), this.contentVersions,
				this.contents, new AuditLogService(this.auditLog, clock), clock, images);
	}

	/** The service of the species, to retire one a character names. */
	SpeciesService speciesService() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new SpeciesValidator(this.species, this.languages, this.chapters, this.episodes);
		var rules = new SpeciesRules(this.contents, this.chapters, this.episodes, this.characters,
				new RulesProperties(5));
		return new SpeciesService(new SpeciesDefinition(this.species, validator, rules), this.contentVersions,
				this.contents, new AuditLogService(this.auditLog, clock), clock, mock(ContentImages.class));
	}

	/**
	 * Complete in both languages of the catalog, so only what a test changes is missing.
	 */
	Character complete(String romaji) {
		return Character.builder()
			.romaji(romaji)
			.translations(Map.of("it", new CharacterTranslation(romaji + " (it)", "Descrizione di " + romaji), "en",
					new CharacterTranslation(romaji + " (en)", "Description of " + romaji)))
			.status(CharacterStatus.ALIVE)
			.gender(Gender.FEMALE)
			.firstAppearance(this.firstAppearance)
			.build();
	}

	/** A new character with its first version in this status. */
	UUID character(VersionStatus status, Character body) {
		var version = new CharacterVersionEntity(workflow(content(EntityType.CHARACTER), status));
		CharacterVersionMapper.rewrite(version, body, EARLIER);
		return this.characters.save(version).getContentId();
	}

	/**
	 * A new species, complete but for its names, with its first version in this status.
	 */
	UUID species(VersionStatus status, String romaji) {
		var version = new SpeciesVersionEntity(workflow(content(EntityType.SPECIES), status));
		SpeciesVersionMapper.rewrite(version, new Species(romaji, Map.of(), null, this.firstAppearance), EARLIER);
		return this.species.save(version).getContentId();
	}

	/** The fields a refusal names. */
	static List<String> fieldsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

	private UUID content(EntityType entityType) {
		return this.contents.save(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	private static ContentVersionEntity workflow(UUID contentId, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(CHOPPER))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
	}

}
