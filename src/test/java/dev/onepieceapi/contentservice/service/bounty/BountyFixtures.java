package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.BountyVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.CharacterVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.validation.BountyValidator;
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
 * What the integration tests of the bounties share: the services built as the application
 * builds them over the repositories of the test, and rows seeded straight into the tables
 * - so a state the service would refuse to build (two drafts that both name the same
 * previous) can be reached.
 */
@Builder
final class BountyFixtures {

	static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

	static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	static final Set<Permission> READER = Set.of(Permission.CONTENT_READ);

	static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final ContentRepository contents;

	private final BountyVersionRepository bounties;

	private final CharacterVersionRepository characters;

	private final ChapterVersionRepository chapters;

	private final EpisodeVersionRepository episodes;

	private final ContentVersionRepository contentVersions;

	private final LanguageRepository languages;

	private final AuditLogRepository auditLog;

	/** The chapter online every complete bounty first appears in; seeded on first use. */
	private StoryPoint firstAppearance;

	StoryPoint firstAppearance() {
		if (this.firstAppearance == null) {
			this.firstAppearance = OnlineChapters.onlineChapter(this.contents, this.chapters);
		}
		return this.firstAppearance;
	}

	BountyChains chains() {
		return new BountyChains(this.bounties);
	}

	BountyService service() {
		return service(this.bounties, mock(ContentImages.class));
	}

	BountyService service(ContentImages images) {
		return service(this.bounties, images);
	}

	/**
	 * The service, its rules reading the bounties through {@code rulesBounties} - which a
	 * race test wraps to hold a caller right after it read.
	 */
	BountyService service(BountyVersionRepository rulesBounties, ContentImages images) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new BountyValidator(this.bounties, this.languages, this.chapters, this.characters,
				this.episodes, chains());
		var rules = new BountyRules(this.contents, rulesBounties, this.chapters, this.characters, this.episodes,
				chains(), new RulesProperties(5));
		return new BountyService(new BountyDefinition(this.bounties, validator, rules), this.contentVersions,
				this.contents, new AuditLogService(this.auditLog, clock), clock, images);
	}

	/**
	 * Complete in both languages of the catalog, so only what a test changes is missing.
	 */
	Bounty complete(String posterName, long amount, UUID previous) {
		return new Bounty(posterName, amount, BountyCondition.DEAD_OR_ALIVE, false, previous, null,
				Map.of("it", new BountyTranslation("Taglia di " + posterName), "en",
						new BountyTranslation("Bounty of " + posterName)),
				null, firstAppearance());
	}

	/** The same bounty, about this character. */
	static Bounty heldBy(Bounty bounty, UUID holder) {
		return new Bounty(bounty.posterName(), bounty.amount(), bounty.condition(), bounty.inactive(),
				bounty.previousContentId(), holder, bounty.translations(), bounty.imageId(), bounty.firstAppearance());
	}

	/** A character, with a single version in this status, for a bounty to be about. */
	UUID character(VersionStatus status, String romaji) {
		UUID contentId = this.contents.save(new ContentEntity(UUID.randomUUID(), EntityType.CHARACTER, EARLIER))
			.getId();
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(CHOPPER))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new CharacterVersionEntity(workflow);
		CharacterVersionMapper.rewrite(version, Character.builder().romaji(romaji).build(), EARLIER);
		this.characters.save(version);
		return contentId;
	}

	/** A new content with its first version in this status. */
	UUID bounty(VersionStatus status, Bounty body) {
		UUID contentId = this.contents.save(new ContentEntity(UUID.randomUUID(), EntityType.BOUNTY, EARLIER)).getId();
		version(contentId, 1, status, body);
		return contentId;
	}

	void version(UUID contentId, int number, VersionStatus status, Bounty body) {
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(CHOPPER))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new BountyVersionEntity(workflow);
		BountyVersionMapper.rewrite(version, body, EARLIER);
		this.bounties.save(version);
	}

	/** The fields a refusal names. */
	static List<String> fieldsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

}
