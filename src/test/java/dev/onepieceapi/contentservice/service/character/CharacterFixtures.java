package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.BountyVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.CharacterVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.SpeciesVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRules;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationDefinition;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationRules;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationService;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.species.SpeciesDefinition;
import dev.onepieceapi.contentservice.service.species.SpeciesRules;
import dev.onepieceapi.contentservice.service.species.SpeciesService;
import dev.onepieceapi.contentservice.service.validation.AffiliationValidator;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	private final BountyVersionRepository bounties;

	/** Only the tests that name an affiliation need it. */
	private final AffiliationVersionRepository affiliations;

	/** Only the tests that name a Devil Fruit need it. */
	private final DevilFruitVersionRepository devilFruits;

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
		return service(images, this.characters);
	}

	/** The service whose rules read the characters through this repository. */
	CharacterService service(CharacterVersionRepository rulesCharacters) {
		return service(mock(ContentImages.class), rulesCharacters);
	}

	private CharacterService service(ContentImages images, CharacterVersionRepository rulesCharacters) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new CharacterValidator(this.characters, this.languages, this.chapters, this.episodes,
				this.species, this.affiliations, this.devilFruits);
		var rules = new CharacterRules(this.contents, this.chapters, this.episodes, this.species, this.affiliations,
				this.bounties, this.devilFruits, rulesCharacters, new RulesProperties(5));
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

	/** The service of the affiliations, to publish and retire one a character names. */
	AffiliationService affiliationService() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new AffiliationValidator(this.affiliations, this.languages, this.chapters, this.episodes);
		var rules = new AffiliationRules(this.contents, this.chapters, this.episodes, this.characters,
				new RulesProperties(5));
		return new AffiliationService(new AffiliationDefinition(this.affiliations, validator, rules),
				this.contentVersions, this.contents, new AuditLogService(this.auditLog, clock), clock,
				mock(ContentImages.class));
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

	/** A later version of a character, in this status. */
	void characterVersion(UUID contentId, int number, VersionStatus status, Character body) {
		var version = new CharacterVersionEntity(workflow(contentId, status, number));
		CharacterVersionMapper.rewrite(version, body, EARLIER);
		this.characters.save(version);
	}

	/**
	 * A new species, complete but for its names, with its first version in this status.
	 */
	UUID species(VersionStatus status, String romaji) {
		var version = new SpeciesVersionEntity(workflow(content(EntityType.SPECIES), status));
		SpeciesVersionMapper.rewrite(version, new Species(romaji, Map.of(), null, this.firstAppearance), EARLIER);
		return this.species.save(version).getContentId();
	}

	/**
	 * A new affiliation of this kind, complete but for its emblem, with its first version
	 * in this status.
	 */
	UUID affiliation(VersionStatus status, String romaji, AffiliationKind kind) {
		UUID contentId = content(EntityType.AFFILIATION);
		affiliationVersion(contentId, 1, status, romaji, kind);
		return contentId;
	}

	/** A later version of an affiliation, in this status and of this kind. */
	void affiliationVersion(UUID contentId, int number, VersionStatus status, String romaji, AffiliationKind kind) {
		var version = new AffiliationVersionEntity(workflow(contentId, status, number));
		AffiliationVersionMapper.rewrite(version, new Affiliation(romaji,
				Map.of("it", new AffiliationTranslation(romaji + " (it)", "Descrizione"), "en",
						new AffiliationTranslation(romaji + " (en)", "Description")),
				null, this.firstAppearance, kind), EARLIER);
		this.affiliations.save(version);
	}

	/**
	 * The rules of the Devil Fruits over the repositories of the test, a type that is
	 * always online: they are what a fruit owes its characters, not its type.
	 */
	DevilFruitRules devilFruitRules() {
		var types = mock(DevilFruitTypeVersionRepository.class);
		when(types.existsWithStatus(any(), anyCollection())).thenReturn(true);
		return new DevilFruitRules(this.contents, types, this.chapters, this.episodes, this.characters,
				new RulesProperties(5));
	}

	/**
	 * A new fruit of a type nothing is known of, complete but for its image, with its
	 * first version in this status.
	 */
	UUID devilFruit(VersionStatus status, String romaji, boolean multipleUsers) {
		var version = new DevilFruitVersionEntity(workflow(content(EntityType.DEVIL_FRUIT), status));
		var translations = Map.of("en", new DevilFruitTranslation(romaji + " (en)", "Description", "Pro", "Con"));
		DevilFruitVersionMapper.rewrite(version, new DevilFruit(romaji, content(EntityType.DEVIL_FRUIT_TYPE), null,
				translations, null, this.firstAppearance, multipleUsers), EARLIER);
		return this.devilFruits.save(version).getContentId();
	}

	/** The version of a fruit with this status, as its rules read it. */
	Version<DevilFruit> devilFruitVersion(UUID contentId) {
		return DevilFruitVersionMapper
			.toDomain(this.devilFruits.findMostRecent(List.of(contentId), VersionStatus.approved()).getFirst());
	}

	/** A new bounty held by this character, with its first version in this status. */
	UUID bounty(VersionStatus status, UUID holder) {
		var version = new BountyVersionEntity(workflow(content(EntityType.BOUNTY), status));
		BountyVersionMapper.rewrite(version, new Bounty("Nami", 16_000_000L, BountyCondition.DEAD_OR_ALIVE, false, null,
				holder, Map.of(), null, this.firstAppearance), EARLIER);
		return this.bounties.save(version).getContentId();
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
		return workflow(contentId, status, 1);
	}

	private static ContentVersionEntity workflow(UUID contentId, VersionStatus status, int number) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.author(UserMapper.toEmbeddable(CHOPPER))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
	}

}
