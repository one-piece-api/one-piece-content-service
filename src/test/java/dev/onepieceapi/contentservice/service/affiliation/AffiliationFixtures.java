package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.validation.AffiliationValidator;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;

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
 * What the integration tests of the affiliations share: the service built as the
 * application builds it over the repositories of the test, and rows seeded straight into
 * the tables.
 */
final class AffiliationFixtures {

	static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

	static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	static final Set<Permission> READER = Set.of(Permission.CONTENT_READ);

	static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final ContentRepository contents;

	private final AffiliationVersionRepository affiliations;

	private final ChapterVersionRepository chapters;

	private final EpisodeVersionRepository episodes;

	private final ContentVersionRepository contentVersions;

	private final LanguageRepository languages;

	private final AuditLogRepository auditLog;

	/** The chapter online every complete affiliation first appears in. */
	private final StoryPoint firstAppearance;

	AffiliationFixtures(ContentRepository contents, AffiliationVersionRepository affiliations,
			ChapterVersionRepository chapters, EpisodeVersionRepository episodes,
			ContentVersionRepository contentVersions, LanguageRepository languages, AuditLogRepository auditLog) {
		this.contents = contents;
		this.affiliations = affiliations;
		this.chapters = chapters;
		this.episodes = episodes;
		this.contentVersions = contentVersions;
		this.languages = languages;
		this.auditLog = auditLog;
		this.firstAppearance = OnlineChapters.onlineChapter(contents, chapters);
	}

	AffiliationService service() {
		return service(mock(ContentImages.class));
	}

	AffiliationService service(ContentImages images) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var validator = new AffiliationValidator(this.affiliations, this.languages, this.chapters, this.episodes);
		var rules = new AffiliationRules(this.contents, this.chapters, this.episodes);
		return new AffiliationService(new AffiliationDefinition(this.affiliations, validator, rules),
				this.contentVersions, this.contents, new AuditLogService(this.auditLog, clock), clock, images);
	}

	/**
	 * Complete in both languages of the catalog, so only what a test changes is missing.
	 */
	Affiliation complete(String romaji) {
		return new Affiliation(romaji,
				Map.of("it", new AffiliationTranslation(romaji + " (it)", "Descrizione di " + romaji), "en",
						new AffiliationTranslation(romaji + " (en)", "Description of " + romaji)),
				null, this.firstAppearance, AffiliationKind.PIRATE_CREW);
	}

	/** The same, of this kind - none for a version written before kinds existed. */
	Affiliation complete(String romaji, AffiliationKind kind) {
		var body = complete(romaji);
		return new Affiliation(body.romaji(), body.translations(), null, body.firstAppearance(), kind);
	}

	/** The same, first appearing elsewhere. */
	Affiliation complete(String romaji, StoryPoint firstAppearanceElsewhere) {
		var body = complete(romaji);
		return new Affiliation(body.romaji(), body.translations(), null, firstAppearanceElsewhere, body.kind());
	}

	/** A new content with its first version in this status. */
	UUID affiliation(VersionStatus status, Affiliation body) {
		UUID contentId = this.contents.save(new ContentEntity(UUID.randomUUID(), EntityType.AFFILIATION, EARLIER))
			.getId();
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(CHOPPER))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new AffiliationVersionEntity(workflow);
		AffiliationVersionMapper.rewrite(version, body, EARLIER);
		this.affiliations.save(version);
		return contentId;
	}

	/** The fields a refusal names. */
	static List<String> fieldsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

}
