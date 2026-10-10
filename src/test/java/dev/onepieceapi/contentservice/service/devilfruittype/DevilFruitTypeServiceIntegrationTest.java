package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.DevilFruitTypeNotFoundException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Runs the read side against a real PostgreSQL (Testcontainers), seeding through the
 * repository: the "one row per content" query, the filters, the pagination totals and the
 * two partial unique indexes are all PostgreSQL's own behaviour.
 * <p>
 * The fleet seeded for every test, with the last update of each content's latest version:
 * <ul>
 * <li>Paramecia - v1 superseded, v2 online, v3 draft by chopper (today)</li>
 * <li>Zoan - v1 online, v2 in review by chopper (yesterday)</li>
 * <li>Logia - v1 draft by nami, nothing else (10 days ago)</li>
 * <li>Kodai Zoan - v1 ready to publish by chopper (40 days ago)</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeServiceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final ContentFilter NO_FILTER = new ContentFilter(null, null, null, null);

	private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

	private static final String ITALIAN = "it";

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	@Autowired
	private DevilFruitTypeVersionRepository versionRepository;

	@Autowired
	private ContentVersionRepository contentVersionRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private DevilFruitVersionRepository fruitRepository;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TestEntityManager entityManager;

	private DevilFruitTypeService service;

	private UUID paramecia;

	private UUID zoan;

	private UUID logia;

	private UUID kodaiZoan;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		this.service = new DevilFruitTypeService(this.versionRepository, this.contentVersionRepository,
				this.contentRepository, validator,
				new DevilFruitTypeRules(this.contentRepository, this.fruitRepository, new RulesProperties(5)),
				auditLogService, clock);

		this.paramecia = content();
		version(this.paramecia, 1, SUPERSEDED, this.nami, 60, names("Paramecia", "Paramisia", "Paramecia"));
		version(this.paramecia, 2, PUBLISHED, this.nami, 30, names("Paramecia", "Paramisia", "Paramecia"));
		version(this.paramecia, 3, DRAFT, this.chopper, 0, names("Chōjin-kei", "Superuomo", "Paramecia"));
		this.zoan = content();
		version(this.zoan, 1, PUBLISHED, this.nami, 20, names("Zoan", "Zoo Zoo", "Zoan"));
		version(this.zoan, 2, IN_REVIEW, this.chopper, 1, names("Dōbutsu-kei", "Zoo Zoo", "Zoan"));
		this.logia = content();
		version(this.logia, 1, DRAFT, this.nami, 10, names("Logia", "Rogia", null));
		this.kodaiZoan = content();
		version(this.kodaiZoan, 1, READY_TO_PUBLISH, this.chopper, 40, names("Kodai Zoan", "Zoo Zoo antico", "100%"));
		// So that every assertion reads what PostgreSQL returns, not what is still
		// cached.
		this.entityManager.flush();
		this.entityManager.clear();
	}

	@Test
	void anEditorSeesEveryContentByItsMostRecentVersionNewestFirst() {
		var page = this.service.list(EDITOR, this.nami, NO_FILTER, FIRST_PAGE, ITALIAN);

		assertThat(page.getContent())
			.extracting(ContentSummary::contentId, row -> row.version().number(), row -> row.version().status(),
					ContentSummary::onlineVersionNumber)
			.containsExactly(tuple(this.paramecia, 3, DRAFT, 2), tuple(this.zoan, 2, IN_REVIEW, 1),
					tuple(this.logia, 1, DRAFT, null), tuple(this.kodaiZoan, 1, READY_TO_PUBLISH, null));
	}

	@Test
	void aReviewerSeesNoDraftSoAContentFallsBackToItsLatestVisibleVersion() {
		var page = this.service.list(REVIEWER, this.nami, NO_FILTER, FIRST_PAGE, ITALIAN);

		assertThat(page.getContent())
			.extracting(ContentSummary::contentId, row -> row.version().number(), row -> row.version().status())
			.containsExactly(tuple(this.zoan, 2, IN_REVIEW), tuple(this.paramecia, 2, PUBLISHED),
					tuple(this.kodaiZoan, 1, READY_TO_PUBLISH));
	}

	@Test
	void aPublisherSeesNothingBeforeReadyToPublish() {
		var page = this.service.list(PUBLISHER, this.nami, NO_FILTER, FIRST_PAGE, ITALIAN);

		assertThat(page.getContent())
			.extracting(ContentSummary::contentId, row -> row.version().number(), row -> row.version().status())
			.containsExactly(tuple(this.zoan, 1, PUBLISHED), tuple(this.paramecia, 2, PUBLISHED),
					tuple(this.kodaiZoan, 1, READY_TO_PUBLISH));
	}

	@Test
	void aRowCarriesTheContentOfItsVersion() {
		var row = this.service
			.list(EDITOR, this.nami, new ContentFilter(null, "kodai", null, null), FIRST_PAGE, ITALIAN)
			.getContent()
			.get(0);

		assertThat(row.version().author()).isEqualTo(this.chopper);
		assertThat(row.version().body().romaji()).isEqualTo("Kodai Zoan");
		assertThat(row.version().body().translations()).containsExactly(
				entry("en",
						new DevilFruitTypeTranslation("100%", "100% description", "100% advantages",
								"100% disadvantages")),
				entry("it", new DevilFruitTypeTranslation("Zoo Zoo antico", "Zoo Zoo antico description",
						"Zoo Zoo antico advantages", "Zoo Zoo antico disadvantages")));
		assertThat(row.version().updatedAt()).isEqualTo(NOW.minus(Duration.ofDays(40)));
	}

	@Test
	void theStatusFilterShowsEachContentByItsVersionInThatStatus() {
		var superseded = this.service.list(EDITOR, this.nami, new ContentFilter(SUPERSEDED, null, null, null),
				FIRST_PAGE, ITALIAN);
		var published = this.service.list(EDITOR, this.nami, new ContentFilter(PUBLISHED, null, null, null), FIRST_PAGE,
				ITALIAN);

		assertThat(superseded.getContent())
			.extracting(ContentSummary::contentId, row -> row.version().number(), ContentSummary::onlineVersionNumber)
			.containsExactly(tuple(this.paramecia, 1, 2));
		assertThat(published.getContent()).extracting(ContentSummary::contentId, row -> row.version().number())
			.containsExactly(tuple(this.zoan, 1), tuple(this.paramecia, 2));
	}

	@Test
	void theStatusFilterIsLimitedToTheStatusesTheCallerSees() {
		var drafts = new ContentFilter(DRAFT, null, null, null);

		assertThat(this.service.list(EDITOR, this.nami, drafts, FIRST_PAGE, ITALIAN).getTotalElements()).isEqualTo(2);
		assertThat(this.service.list(REVIEWER, this.nami, drafts, FIRST_PAGE, ITALIAN).getTotalElements()).isZero();
		assertThat(this.service.list(REVIEWER, this.nami, drafts, FIRST_PAGE, ITALIAN).getContent()).isEmpty();
	}

	@Test
	void theTextFilterMatchesTheRomajiOrANameInAnyLanguageIgnoringCase() {
		assertThat(idsOf(EDITOR, new ContentFilter(null, "DŌBUTSU", null, null))).containsExactly(this.zoan);
		assertThat(idsOf(EDITOR, new ContentFilter(null, "superuomo", null, null))).containsExactly(this.paramecia);
		assertThat(idsOf(EDITOR, new ContentFilter(null, "  zoo zoo ", null, null))).containsExactly(this.zoan,
				this.kodaiZoan);
		assertThat(idsOf(EDITOR, new ContentFilter(null, "nothing like this", null, null))).isEmpty();
	}

	@Test
	void theTextFilterLooksAtTheVersionShownNotAtOlderOnes() {
		// "Paramisia" is the Italian name of v1 and v2, no longer of the draft v3.
		assertThat(idsOf(EDITOR, new ContentFilter(null, "paramisia", null, null))).isEmpty();
		assertThat(idsOf(REVIEWER, new ContentFilter(null, "paramisia", null, null))).containsExactly(this.paramecia);
	}

	@Test
	void theTextFilterTakesWildcardCharactersLiterally() {
		assertThat(idsOf(EDITOR, new ContentFilter(null, "100%", null, null))).containsExactly(this.kodaiZoan);
		assertThat(idsOf(EDITOR, new ContentFilter(null, "%", null, null))).containsExactly(this.kodaiZoan);
		assertThat(idsOf(EDITOR, new ContentFilter(null, "_", null, null))).isEmpty();
	}

	@Test
	void theAuthorFilterLooksAtTheVersionShown() {
		var byChopper = new ContentFilter(null, null, this.chopper.username(), null);

		assertThat(idsOf(EDITOR, byChopper)).containsExactly(this.paramecia, this.zoan, this.kodaiZoan);
		// For a publisher Paramecia and Zoan are shown by nami's online versions.
		assertThat(idsOf(PUBLISHER, byChopper)).containsExactly(this.kodaiZoan);
	}

	@Test
	void theLastUpdateFilterCountsWholeDaysBackFromToday() {
		assertThat(idsOf(EDITOR, new ContentFilter(null, null, null, 0))).containsExactly(this.paramecia);
		assertThat(idsOf(EDITOR, new ContentFilter(null, null, null, 7))).containsExactly(this.paramecia, this.zoan);
		assertThat(idsOf(EDITOR, new ContentFilter(null, null, null, 30))).containsExactly(this.paramecia, this.zoan,
				this.logia);
	}

	@Test
	void theTotalCountsContentsNotVersionsAndRespectsVisibilityAndFilters() {
		var firstOfTwo = PageRequest.of(0, 2);

		// Seven versions, four contents.
		assertThat(this.service.list(EDITOR, this.nami, NO_FILTER, firstOfTwo, ITALIAN).getTotalElements())
			.isEqualTo(4);
		assertThat(this.service.list(EDITOR, this.nami, NO_FILTER, firstOfTwo, ITALIAN).getTotalPages()).isEqualTo(2);
		assertThat(this.service.list(REVIEWER, this.nami, NO_FILTER, firstOfTwo, ITALIAN).getTotalElements())
			.isEqualTo(3);
		var byChopper = new ContentFilter(null, null, this.chopper.username(), null);
		assertThat(this.service.list(PUBLISHER, this.nami, byChopper, firstOfTwo, ITALIAN).getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void pagesFollowEachOtherAndAPageBeyondTheLastIsEmpty() {
		assertThat(idsOf(EDITOR, NO_FILTER, PageRequest.of(0, 2))).containsExactly(this.paramecia, this.zoan);
		assertThat(idsOf(EDITOR, NO_FILTER, PageRequest.of(1, 2))).containsExactly(this.logia, this.kodaiZoan);

		var beyond = this.service.list(EDITOR, this.nami, NO_FILTER, PageRequest.of(2, 2), ITALIAN);
		assertThat(beyond.getContent()).isEmpty();
		assertThat(beyond.getTotalElements()).isEqualTo(4);
	}

	@Test
	void noContentAppearsOnTwoPagesEvenWhenEveryRowHasTheSameLastUpdate() {
		// Five more contents, all updated at the same instant: only the tie-breaker
		// orders
		// them.
		IntStream.range(0, 5)
			.forEach(i -> version(content(), 1, SUPERSEDED, this.zoro, 5, names("Tie " + i, "Pari " + i, "Tie " + i)));
		this.entityManager.flush();
		this.entityManager.clear();
		var byZoro = new ContentFilter(null, null, this.zoro.username(), null);

		var seen = IntStream.range(0, 3)
			.mapToObj(page -> idsOf(PUBLISHER, byZoro, PageRequest.of(page, 2)))
			.flatMap(List::stream)
			.toList();

		assertThat(seen).hasSize(5).doesNotHaveDuplicates();
	}

	@Test
	void theListCanBeSortedByRomaji() {
		var byRomaji = PageRequest.of(0, 20, Sort.by("romaji"));

		assertThat(idsOf(PUBLISHER, NO_FILTER, byRomaji)).containsExactly(this.kodaiZoan, this.paramecia, this.zoan);
	}

	@Test
	void theListSortedByNameFollowsTheNameInTheCallersLanguageOrElseTheRomaji() {
		var byName = PageRequest.of(0, 20, Sort.by("name"));

		// Rogia, Superuomo, Zoo Zoo, Zoo Zoo antico.
		assertThat(idsOf(EDITOR, NO_FILTER, byName, "it")).containsExactly(this.logia, this.paramecia, this.zoan,
				this.kodaiZoan);
		// 100%, Logia (no English name: its romaji), Paramecia, Zoan.
		assertThat(idsOf(EDITOR, NO_FILTER, byName, "en")).containsExactly(this.kodaiZoan, this.logia, this.paramecia,
				this.zoan);
		assertThat(idsOf(EDITOR, NO_FILTER, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "name")), "it"))
			.containsExactly(this.kodaiZoan, this.zoan, this.paramecia, this.logia);
	}

	@Test
	void sortingByNameDoesNotChangeTheTotal() {
		var byName = PageRequest.of(0, 2, Sort.by("name"));

		var page = this.service.list(EDITOR, this.nami, NO_FILTER, byName, ITALIAN);

		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getContent()).hasSize(2);
	}

	@Test
	void aRowWithoutANameComesLastWhicheverTheDirection() {
		var unnamed = content();
		version(unnamed, 1, DRAFT, this.zoro, 3, names(null, null, null));
		this.entityManager.flush();
		this.entityManager.clear();

		assertThat(idsOf(EDITOR, NO_FILTER, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name")))).last()
			.isEqualTo(unnamed);
		assertThat(idsOf(EDITOR, NO_FILTER, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "name")))).last()
			.isEqualTo(unnamed);
	}

	@Test
	void theListSortedByStatusFollowsTheLifecycleNotTheAlphabet() {
		// In review, ready to publish, published - alphabetically "published" would come
		// second.
		assertThat(idsOf(REVIEWER, NO_FILTER, PageRequest.of(0, 20, Sort.by("status")))).containsExactly(this.zoan,
				this.kodaiZoan, this.paramecia);
		assertThat(idsOf(REVIEWER, NO_FILTER, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "status"))))
			.containsExactly(this.paramecia, this.kodaiZoan, this.zoan);
	}

	@Test
	void theListSortedByAuthorThenByLastUpdateKeepsBothOrders() {
		var byAuthorThenNewest = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("author"), Sort.Order.desc("updatedAt")));

		// chopper's three rows, newest first, then nami's.
		assertThat(idsOf(EDITOR, NO_FILTER, byAuthorThenNewest)).containsExactly(this.paramecia, this.zoan,
				this.kodaiZoan, this.logia);
	}

	@Test
	void theSummaryCountsWhatTheCallerSeesAndHowMuchOfItIsTheirs() {
		var forNami = this.service.summary(EDITOR, this.nami);
		var forChopper = this.service.summary(EDITOR, this.chopper);

		assertThat(forNami.total()).isEqualTo(4);
		// Only Logia is shown by a version of nami: Paramecia and Zoan by chopper's.
		assertThat(forNami.mine()).isEqualTo(1);
		assertThat(forChopper.total()).isEqualTo(4);
		assertThat(forChopper.mine()).isEqualTo(3);
		assertThat(forNami.statuses()).containsExactly(VersionStatus.values());
	}

	@Test
	void theSummaryOfAReviewerLeavesOutDraftsAndRejectedVersions() {
		var summary = this.service.summary(REVIEWER, this.zoro);

		assertThat(summary.total()).isEqualTo(3);
		assertThat(summary.mine()).isZero();
		assertThat(summary.statuses()).doesNotContain(DRAFT, VersionStatus.REJECTED).hasSize(6);
	}

	@Test
	void theShareOfTheCallerFollowsTheVersionsTheirPermissionsShow() {
		// With a publisher's permissions Paramecia and Zoan fall back to nami's online
		// versions.
		assertThat(this.service.summary(PUBLISHER, this.nami).mine()).isEqualTo(2);
		assertThat(this.service.summary(PUBLISHER, this.chopper).mine()).isEqualTo(1);
	}

	@Test
	void theSummaryOfACallerWhoSeesNothingIsEmpty() {
		var summary = this.service.summary(Set.of(), this.nami);

		assertThat(summary.total()).isZero();
		assertThat(summary.mine()).isZero();
		assertThat(summary.statuses()).isEmpty();
	}

	@Test
	void theMineCountIsWhatTheAuthorFilterWouldList() {
		var byChopper = ContentFilter.authoredBy(this.chopper.username());

		var listed = this.service.list(EDITOR, this.nami, byChopper, FIRST_PAGE, ITALIAN).getTotalElements();

		assertThat(this.service.summary(EDITOR, this.chopper).mine()).isEqualTo(listed);
	}

	@Test
	void theAuthorsAreThoseOfTheVersionsTheCallerSees() {
		version(content(), 1, DRAFT, this.zoro, 2, names("Tokushu", "Speciale", "Special"));

		assertThat(this.service.authors(EDITOR)).containsExactly(this.chopper, this.nami, this.zoro);
		assertThat(this.service.authors(REVIEWER)).containsExactly(this.chopper, this.nami);
	}

	@Test
	void aContentComesWithItsVisibleVersionsOldestFirst() {
		var forEditor = this.service.get(EDITOR, this.nami, this.paramecia);
		var forReviewer = this.service.get(REVIEWER, this.nami, this.paramecia);

		assertThat(forEditor.versions()).extracting(VersionAccess::version)
			.extracting(Version::number, Version::status, Version::basedOn)
			.containsExactly(tuple(1, SUPERSEDED, null), tuple(2, PUBLISHED, 1), tuple(3, DRAFT, 2));
		assertThat(forEditor.onlineVersionNumber()).contains(2);
		assertThat(forReviewer.versions()).extracting(VersionAccess::version)
			.extracting(Version::number)
			.containsExactly(1, 2);
	}

	@Test
	void aContentWithOnlyADraftDoesNotExistForAReviewer() {
		assertThat(this.service.get(EDITOR, this.nami, this.logia).onlineVersionNumber()).isEmpty();
		assertThatThrownBy(() -> this.service.get(REVIEWER, this.nami, this.logia))
			.isInstanceOf(DevilFruitTypeNotFoundException.class);
		assertThatThrownBy(() -> this.service.get(EDITOR, this.nami, UUID.randomUUID()))
			.isInstanceOf(DevilFruitTypeNotFoundException.class);
	}

	@Test
	void aVersionIsReadWithItsContentOnlyByWhoSeesItsStatus() {
		var review = this.service.getVersion(REVIEWER, this.zoro, this.zoan, 2).version();

		assertThat(review.status()).isEqualTo(IN_REVIEW);
		assertThat(review.author()).isEqualTo(this.chopper);
		assertThat(review.claimant()).isNull();
		assertThat(review.body().romaji()).isEqualTo("Dōbutsu-kei");
		assertThat(review.body().translations().get("it").name()).isEqualTo("Zoo Zoo");
		assertThatThrownBy(() -> this.service.getVersion(PUBLISHER, this.zoro, this.zoan, 2))
			.isInstanceOf(VersionNotFoundException.class);
		assertThatThrownBy(() -> this.service.getVersion(EDITOR, this.nami, this.zoan, 9))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void aVersionInReviewShowsWhoHoldsIt() {
		var inReview = this.versionRepository.findVisible(this.zoan, 2, Set.of(IN_REVIEW)).orElseThrow();
		inReview.getVersion().setClaimant(UserMapper.toEmbeddable(this.zoro));
		this.entityManager.flush();
		this.entityManager.clear();

		var held = this.service.getVersion(REVIEWER, this.zoro, this.zoan, 2).version();

		assertThat(held.claimant()).isEqualTo(this.zoro);
	}

	@Test
	void aVersionComesWithWhatItsCallerMayDoWithIt() {
		var forItsAuthor = this.service.getVersion(EDITOR, this.chopper, this.paramecia, 3);
		var forAnotherEditor = this.service.getVersion(EDITOR, this.nami, this.paramecia, 3);
		var forAReviewer = this.service.getVersion(REVIEWER, this.zoro, this.zoan, 2);

		assertThat(forItsAuthor.allowedActions()).containsExactly(VersionAction.EDIT, VersionAction.DELETE,
				VersionAction.SUBMIT);
		assertThat(forAnotherEditor.allowedActions()).isEmpty();
		assertThat(forAReviewer.allowedActions()).containsExactly(VersionAction.CLAIM);
	}

	@Test
	void anOpenVersionBlocksANewDraftOfItsContent() {
		// Paramecia has a draft open, so its online version cannot start another one.
		var online = this.service.getVersion(EDITOR, this.nami, this.paramecia, 2);

		assertThat(online.allowedActions()).isEmpty();
	}

	@Test
	void anOpenVersionBlocksARecoveryEvenFromWhoCannotSeeIt() {
		UUID free = content();
		version(free, 1, ARCHIVED, this.nami, 5, names("Free", "Libero", "Free"));
		UUID busy = content();
		version(busy, 1, ARCHIVED, this.nami, 5, names("Busy", "Occupato", "Busy"));
		version(busy, 2, DRAFT, this.chopper, 1, names("Busy", "Occupato", "Busy"));
		this.entityManager.flush();
		this.entityManager.clear();

		// A publisher does not see the draft of the second content, yet it counts.
		assertThat(this.service.get(PUBLISHER, this.nami, busy).versions()).hasSize(1);
		assertThat(this.service.getVersion(PUBLISHER, this.zoro, free, 1).allowedActions())
			.containsExactly(VersionAction.RECOVER);
		assertThat(this.service.getVersion(PUBLISHER, this.zoro, busy, 1).allowedActions()).isEmpty();
	}

	@Test
	void aContentHasAnOpenVersionWhileOneIsStillMovingThroughTheWorkflow() {
		UUID closed = content();
		version(closed, 1, SUPERSEDED, this.nami, 9, names("Closed", "Chiuso", "Closed"));
		version(closed, 2, PUBLISHED, this.nami, 5, names("Closed", "Chiuso", "Closed"));
		this.entityManager.flush();

		assertThat(this.contentVersionRepository.hasOpenVersion(this.paramecia)).isTrue();
		assertThat(this.contentVersionRepository.hasOpenVersion(this.zoan)).isTrue();
		assertThat(this.contentVersionRepository.hasOpenVersion(this.kodaiZoan)).isTrue();
		assertThat(this.contentVersionRepository.hasOpenVersion(closed)).isFalse();
		assertThat(this.contentVersionRepository.hasOpenVersion(UUID.randomUUID())).isFalse();
	}

	@Test
	void theEventsOfAVersionAreItsAuditRecordsOldestFirst() {
		audit("VERSION_SUBMITTED", this.chopper, this.zoan, 2, null, NOW.minusSeconds(60));
		audit("VERSION_CREATED", this.chopper, this.zoan, 2, null, NOW.minusSeconds(120));
		audit("VERSION_PUBLISHED", this.nami, this.zoan, 1, null, NOW.minusSeconds(30));
		audit("VERSION_REJECTED", this.zoro, this.paramecia, 2, "Too short", NOW);
		// A draft of Zoan deleted long ago: same content, possibly the same number,
		// another
		// id.
		audit("VERSION_DELETED", this.nami, this.zoan, UUID.randomUUID(), null, NOW);

		assertThat(this.service.events(REVIEWER, this.zoan, 2))
			.extracting(VersionEvent::action, VersionEvent::actor, VersionEvent::occurredAt)
			.containsExactly(tuple("VERSION_CREATED", this.chopper, NOW.minusSeconds(120)),
					tuple("VERSION_SUBMITTED", this.chopper, NOW.minusSeconds(60)));
		assertThat(this.service.events(REVIEWER, this.paramecia, 2)).extracting(VersionEvent::detail)
			.containsExactly("Too short");
	}

	@Test
	void theEventsOfAVersionTheCallerDoesNotSeeAreNotFound() {
		audit("VERSION_CREATED", this.nami, this.logia, 1, null, NOW);

		assertThat(this.service.events(EDITOR, this.logia, 1)).hasSize(1);
		assertThatThrownBy(() -> this.service.events(REVIEWER, this.logia, 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void theDatabaseRefusesASecondOpenVersionOfAContent() {
		var second = new DevilFruitTypeVersionEntity(workflow(this.zoan, 3, 1, this.nami, DRAFT, NOW));

		assertThatThrownBy(() -> this.versionRepository.saveAndFlush(second))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_content_version_open");
	}

	@Test
	void theDatabaseRefusesASecondOnlineVersionOfAContent() {
		var second = new DevilFruitTypeVersionEntity(workflow(this.kodaiZoan, 2, 1, this.nami, PUBLISHED, NOW));
		this.versionRepository.saveAndFlush(second);
		var third = new DevilFruitTypeVersionEntity(workflow(this.kodaiZoan, 3, 2, this.nami, PUBLISHED, NOW));

		assertThatThrownBy(() -> this.versionRepository.saveAndFlush(third))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_content_version_online");
	}

	@Test
	void theDatabaseRefusesAVersionNumberUsedTwiceInAContent() {
		var again = new DevilFruitTypeVersionEntity(workflow(this.kodaiZoan, 1, null, this.nami, SUPERSEDED, NOW));

		assertThatThrownBy(() -> this.versionRepository.saveAndFlush(again))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_content_version_number");
	}

	@Test
	void theDatabaseRefusesAVersionNotBasedOnAnEarlierOne() {
		var basedOnItself = new DevilFruitTypeVersionEntity(workflow(this.logia, 2, 2, this.nami, SUPERSEDED, NOW));

		assertThatThrownBy(() -> this.versionRepository.saveAndFlush(basedOnItself))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_content_version_based_on");
	}

	@Test
	void theDatabaseRefusesAnEntityTypeItDoesNotKnow() {
		var insert = this.entityManager.getEntityManager()
			.createNativeQuery("insert into content (id, entity_type, created_at) values (?1, 'SEA_KING', now())")
			.setParameter(1, UUID.randomUUID());

		assertThatThrownBy(insert::executeUpdate).hasMessageContaining("content_entity_type_check");
	}

	@Test
	void removingAContentRemovesItsVersionsAndWhatTheySay() {
		this.entityManager.getEntityManager()
			.createNativeQuery("delete from content where id = ?1")
			.setParameter(1, this.paramecia)
			.executeUpdate();
		this.entityManager.clear();

		assertThatThrownBy(() -> this.service.get(EDITOR, this.nami, this.paramecia))
			.isInstanceOf(DevilFruitTypeNotFoundException.class);
		assertThat(this.service.list(EDITOR, this.nami, NO_FILTER, FIRST_PAGE, ITALIAN).getTotalElements())
			.isEqualTo(3);
	}

	private List<UUID> idsOf(Set<Permission> permissions, ContentFilter filter) {
		return idsOf(permissions, filter, FIRST_PAGE);
	}

	private List<UUID> idsOf(Set<Permission> permissions, ContentFilter filter, Pageable pageable) {
		return idsOf(permissions, filter, pageable, ITALIAN);
	}

	private List<UUID> idsOf(Set<Permission> permissions, ContentFilter filter, Pageable pageable, String language) {
		return this.service.list(permissions, this.nami, filter, pageable, language)
			.map(ContentSummary::contentId)
			.getContent();
	}

	private UUID content() {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT_TYPE, NOW))
			.getId();
	}

	/**
	 * Seeds one version, based on the previous one and last updated {@code daysAgo} days
	 * before {@link #NOW}.
	 */
	private void version(UUID contentId, int number, VersionStatus status, User author, int daysAgo, Names names) {
		var updatedAt = NOW.minus(Duration.ofDays(daysAgo));
		var basedOn = number == 1 ? null : number - 1;
		var version = new DevilFruitTypeVersionEntity(workflow(contentId, number, basedOn, author, status, updatedAt));
		version.setRomaji(names.romaji());
		version.getTranslations().put("it", translation(names.italian()));
		if (names.english() != null) {
			version.getTranslations().put("en", translation(names.english()));
		}
		this.versionRepository.save(version);
	}

	/** A null English name leaves that language out of the version. */
	private static Names names(String romaji, String italian, String english) {
		return new Names(romaji, italian, english);
	}

	private static TranslationEmbeddable translation(String name) {
		return new TranslationEmbeddable(name, name + " description", name + " advantages", name + " disadvantages");
	}

	private void audit(String action, User actor, UUID contentId, int versionNumber, String detail, Instant when) {
		var everyStatus = Set.of(VersionStatus.values());
		var versionId = this.versionRepository.findVisible(contentId, versionNumber, everyStatus)
			.orElseThrow()
			.getVersionId();
		audit(action, actor, contentId, versionId, detail, when);
	}

	private void audit(String action, User actor, UUID contentId, UUID versionId, String detail, Instant when) {
		var entry = AuditLogEntity.builder()
			.action(action)
			.actor(UserMapper.toEmbeddable(actor))
			.targetContentId(contentId)
			.targetVersionId(versionId)
			.detail(detail)
			.occurredAt(when)
			.build();
		this.auditLogRepository.save(entry);
	}

	private static ContentVersionEntity workflow(UUID contentId, int number, Integer basedOn, User author,
			VersionStatus status, Instant at) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(basedOn)
			.author(UserMapper.toEmbeddable(author))
			.status(status)
			.createdAt(at)
			.updatedAt(at)
			.build();
	}

	private record Names(String romaji, String italian, String english) {

	}

}
