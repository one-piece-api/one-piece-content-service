package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyReference;
import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.READER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The list of bounties and what each caller sees of the chain around a bounty, against a
 * real PostgreSQL (Testcontainers): the search by key and the sort by name - a bounty has
 * no localized name, so both read the key alone - the sort by amount, the filters by
 * condition and by validity, and the validity itself: derived from the bounties the
 * caller sees, never stored (implementation plan of the Bounty, D3, D5).
 * <p>
 * Seeded for every test, all by chopper: Luffy 1,500,000,000 online; Luffy 3,000,000,000,
 * a draft replacing it; Zoro 320,000,000 online, only alive; Nami 16,000,000 online and
 * inactive; Robin 79,000,000 online, only alive.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class BountyListIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final String LANGUAGE = "it";

	@Autowired
	private BountyVersionRepository bountyRepository;

	@Autowired
	private CharacterVersionRepository characterRepository;

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
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entityManager;

	private BountyFixtures fixtures;

	private BountyService service;

	private BountyLinks links;

	private UUID luffyFirst;

	private UUID luffySecond;

	private UUID zoro;

	private UUID nami;

	private UUID robin;

	@BeforeEach
	void setUp() {
		this.fixtures = BountyFixtures.builder()
			.contents(this.contentRepository)
			.bounties(this.bountyRepository)
			.characters(this.characterRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.build();
		this.service = this.fixtures.service();
		this.links = new BountyLinks(this.bountyRepository, Clock.fixed(BountyFixtures.NOW, ZoneOffset.UTC));
		this.luffyFirst = this.fixtures.bounty(PUBLISHED,
				this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		this.luffySecond = this.fixtures.bounty(DRAFT,
				this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, this.luffyFirst));
		this.zoro = this.fixtures.bounty(PUBLISHED,
				onlyAlive(this.fixtures.complete("Roronoa Zoro", 320_000_000L, null)));
		this.nami = this.fixtures.bounty(PUBLISHED, inactive(this.fixtures.complete("Nami", 16_000_000L, null)));
		this.robin = this.fixtures.bounty(PUBLISHED,
				onlyAlive(this.fixtures.complete("Nico Robin", 79_000_000L, null)));
		stored();
	}

	@Test
	void theSearchReadsTheKeyAlone() {
		assertThat(keysOf(EDITOR, "luffy")).containsExactlyInAnyOrder("Monkey D. Luffy 1500000000",
				"Monkey D. Luffy 3000000000");
		assertThat(search(EDITOR, new ContentFilter(null, "RORONOA", null, null))).containsExactly(this.zoro);
		assertThat(search(EDITOR, new ContentFilter(null, "  nico ", null, null))).containsExactly(this.robin);
		assertThat(search(EDITOR, new ContentFilter(null, "gomu", null, null))).isEmpty();
	}

	@Test
	void aSortByNameIsASortByTheKeyWhateverTheCase() {
		var page = this.service.list(EDITOR, CHOPPER, ContentFilter.none(), PageRequest.of(0, 20, Sort.by("name")),
				LANGUAGE);

		assertThat(page.getContent()).extracting(ContentSummary::contentId)
			.containsExactly(this.luffyFirst, this.luffySecond, this.nami, this.robin, this.zoro);
	}

	@Test
	void aSortByAmountOrdersByTheBerry() {
		var descending = this.service.list(EDITOR, CHOPPER, ContentFilter.none(),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount")), LANGUAGE);
		var ascending = this.service.list(EDITOR, CHOPPER, ContentFilter.none(),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "amount")), LANGUAGE);

		assertThat(descending.getContent()).extracting(ContentSummary::contentId)
			.containsExactly(this.luffySecond, this.luffyFirst, this.zoro, this.robin, this.nami);
		assertThat(ascending.getContent()).extracting(ContentSummary::contentId)
			.containsExactly(this.nami, this.robin, this.zoro, this.luffyFirst, this.luffySecond);
	}

	@Test
	void theListIsNarrowedByCondition() {
		var onlyAlive = new ContentFilter(null, null, null, null).withAttribute("condition",
				BountyCondition.ONLY_ALIVE);

		assertThat(search(EDITOR, onlyAlive)).containsExactlyInAnyOrder(this.zoro, this.robin);
		assertThat(search(EDITOR, onlyAlive.withAttribute("condition", BountyCondition.DEAD_OR_ALIVE)))
			.containsExactlyInAnyOrder(this.luffyFirst, this.luffySecond, this.nami);
	}

	@Test
	void theListIsNarrowedByValidityAsTheCallerSeesTheOtherBounties() {
		// An editor sees the draft replacing the first Luffy; a reader does not see it at
		// all.
		assertThat(search(EDITOR, validity(BountyValidity.SUPERSEDED))).containsExactly(this.luffyFirst);
		assertThat(search(EDITOR, validity(BountyValidity.ACTIVE))).containsExactlyInAnyOrder(this.luffySecond,
				this.zoro, this.robin);
		assertThat(search(EDITOR, validity(BountyValidity.INACTIVE))).containsExactly(this.nami);

		assertThat(search(READER, validity(BountyValidity.SUPERSEDED))).isEmpty();
		assertThat(search(READER, validity(BountyValidity.ACTIVE))).containsExactlyInAnyOrder(this.luffyFirst,
				this.zoro, this.robin);
	}

	@Test
	void validityAndConditionNarrowTogetherWithTheCommonFilters() {
		var filter = validity(BountyValidity.ACTIVE).withAttribute("condition", BountyCondition.ONLY_ALIVE);

		assertThat(search(EDITOR, filter)).containsExactlyInAnyOrder(this.zoro, this.robin);
		assertThat(search(EDITOR, new ContentFilter(null, "zoro", null, null, Map.of(), filter.attributes())))
			.containsExactly(this.zoro);
	}

	@Test
	void theTotalCountsWhatTheFiltersLeave() {
		var page = this.service.list(EDITOR, CHOPPER, validity(BountyValidity.ACTIVE), PageRequest.of(0, 2), LANGUAGE);

		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getContent()).hasSize(2);
	}

	@Test
	void aBountyIsActiveThenSupersededOnceTheSuccessorIsOnlineAndVisibleThenInactiveOverridesBoth() {
		assertThat(search(READER, validity(BountyValidity.ACTIVE))).contains(this.luffyFirst);

		this.jdbc.update("update content_version set status = 'PUBLISHED' where content_id = ?", this.luffySecond);
		stored();
		assertThat(search(READER, validity(BountyValidity.SUPERSEDED))).containsExactly(this.luffyFirst);
		assertThat(search(READER, validity(BountyValidity.ACTIVE))).containsExactlyInAnyOrder(this.luffySecond,
				this.zoro, this.robin);

		this.jdbc.update("update bounty_version set inactive = true where version_id in "
				+ "(select id from content_version where content_id = ?)", this.luffyFirst);
		stored();
		assertThat(search(READER, validity(BountyValidity.INACTIVE))).containsExactlyInAnyOrder(this.luffyFirst,
				this.nami);
		assertThat(search(READER, validity(BountyValidity.SUPERSEDED))).isEmpty();
	}

	@Test
	void whichBountiesAreReplacedDependsOnWhatTheCallerSees() {
		var all = List.of(this.luffyFirst, this.luffySecond, this.zoro);

		assertThat(this.links.replacedAmong(all, VisibilityPolicy.visibleStatuses(EDITOR)))
			.containsExactly(this.luffyFirst);
		assertThat(this.links.replacedAmong(all, VisibilityPolicy.visibleStatuses(READER))).isEmpty();
		assertThat(this.links.replacedAmong(List.of(), VisibilityPolicy.visibleStatuses(EDITOR))).isEmpty();
	}

	@Test
	void theChainIsTheBountiesOneReplacesAfterTheOtherNewestFirstThroughWhatTheCallerSees() {
		UUID third = this.fixtures.bounty(DRAFT,
				this.fixtures.complete("Monkey D. Luffy", 4_000_000_000L, this.luffySecond));
		stored();
		var editor = VisibilityPolicy.visibleStatuses(EDITOR);

		var fromTheMiddle = this.links.chainOf(this.luffySecond, this.luffyFirst, editor);
		var fromTheOldest = this.links.chainOf(this.luffyFirst, null, editor);

		assertThat(fromTheMiddle.contentIds()).containsExactly(third, this.luffySecond, this.luffyFirst);
		assertThat(fromTheMiddle.next()).isEqualTo(third);
		assertThat(fromTheOldest.contentIds()).containsExactly(third, this.luffySecond, this.luffyFirst);
		assertThat(this.links.chainOf(this.zoro, null, editor).contentIds()).containsExactly(this.zoro);
		assertThat(this.links.chainOf(this.zoro, null, editor).next()).isNull();
		assertThat(this.links.chainOf(this.luffyFirst, null, VisibilityPolicy.visibleStatuses(READER)).contentIds())
			.containsExactly(this.luffyFirst);
	}

	@Test
	void aChainClosedOnItselfBetweenDraftsEndsTheWalkInsteadOfLooping() {
		// The save check looks at approved versions only, so two drafts can name each
		// other.
		UUID a = this.fixtures.bounty(DRAFT, this.fixtures.complete("Brook", 1L, null));
		UUID b = this.fixtures.bounty(DRAFT, this.fixtures.complete("Brook", 2L, a));
		this.jdbc.update("update bounty_version set previous_content_id = ? where poster_name = 'Brook' and amount = 1",
				b);
		stored();

		var chain = this.links.chainOf(a, b, VisibilityPolicy.visibleStatuses(EDITOR));

		assertThat(chain.contentIds()).containsExactlyInAnyOrder(a, b);
	}

	@Test
	void theBountiesAreCalledByTheirKeyAsTheCallerSeesThemOrAsLastApproved() {
		UUID unnamed = UUID.randomUUID();

		var references = this.links.referencesOf(List.of(this.luffySecond, this.luffyFirst, unnamed),
				VisibilityPolicy.visibleStatuses(READER));

		assertThat(references.get(this.luffyFirst)).isEqualTo(
				new BountyReference(this.luffyFirst, "Monkey D. Luffy 1500000000", "Monkey D. Luffy", 1_500_000_000L));
		// A reader sees no draft: the second Luffy is known by its id alone.
		assertThat(references.get(this.luffySecond)).isEqualTo(BountyReference.unnamed(this.luffySecond));
		assertThat(references.get(unnamed)).isEqualTo(BountyReference.unnamed(unnamed));
		assertThat(this.links.referencesOf(List.of(), VisibilityPolicy.visibleStatuses(READER))).isEmpty();
	}

	@Test
	void theBountiesThatCanBeLinkedAreThoseWithAnApprovedVersionByKeyNarrowedByTheirKey() {
		var all = this.links.linkable(null, 0, 20);
		var luffy = this.links.linkable("luffy", 0, 20);

		assertThat(all.getContent()).extracting(BountyReference::key)
			.containsExactly("Monkey D. Luffy 1500000000", "Nami 16000000", "Nico Robin 79000000",
					"Roronoa Zoro 320000000");
		assertThat(luffy.getContent()).extracting(BountyReference::id).containsExactly(this.luffyFirst);
	}

	@Test
	void theListIsNarrowedByHolder() {
		UUID holder = this.fixtures.character(PUBLISHED, "Nami");
		UUID heldByNami = this.fixtures.bounty(DRAFT,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 66_000_000L, null), holder));
		stored();
		var held = new ContentFilter(null, null, null, null).withAttribute("holder", holder);

		assertThat(search(EDITOR, held)).containsExactly(heldByNami);
		assertThat(search(READER, held)).isEmpty();
		assertThat(search(EDITOR, held.withAttribute("holder", UUID.randomUUID()))).isEmpty();
	}

	@Test
	void theBountiesOfACharacterAreCountedAsTheCallerSeesThem() {
		UUID holder = this.fixtures.character(PUBLISHED, "Nami");
		UUID idle = this.fixtures.character(PUBLISHED, "Zoro");
		this.fixtures.bounty(PUBLISHED,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 16_000_000L, null), holder));
		this.fixtures.bounty(DRAFT, BountyFixtures.heldBy(this.fixtures.complete("Nami", 66_000_000L, null), holder));
		stored();

		assertThat(this.links.bountyCounts(EDITOR, List.of(holder, idle))).containsOnly(entry(holder, 2L));
		assertThat(this.links.bountyCounts(READER, List.of(holder, idle))).containsOnly(entry(holder, 1L));
		assertThat(this.links.bountyCounts(Set.of(), List.of(holder))).isEmpty();
		assertThat(this.links.bountyCounts(EDITOR, List.of())).isEmpty();
	}

	private List<UUID> search(Set<Permission> permissions, ContentFilter filter) {
		return this.service.list(permissions, CHOPPER, filter, PageRequest.of(0, 20), LANGUAGE)
			.getContent()
			.stream()
			.map(ContentSummary::contentId)
			.toList();
	}

	private List<String> keysOf(Set<Permission> permissions, String text) {
		return this.service
			.list(permissions, CHOPPER, new ContentFilter(null, text, null, null), PageRequest.of(0, 20), LANGUAGE)
			.getContent()
			.stream()
			.map(row -> row.version().body().naturalKeyText())
			.toList();
	}

	private static ContentFilter validity(BountyValidity validity) {
		return new ContentFilter(null, null, null, null).withAttribute("validity", validity);
	}

	private static Bounty onlyAlive(Bounty bounty) {
		return new Bounty(bounty.posterName(), bounty.amount(), BountyCondition.ONLY_ALIVE, bounty.inactive(),
				bounty.previousContentId(), null, bounty.translations(), null, bounty.firstAppearance());
	}

	private static Bounty inactive(Bounty bounty) {
		return new Bounty(bounty.posterName(), bounty.amount(), bounty.condition(), true, bounty.previousContentId(),
				null, Map.of("it", new BountyTranslation("Revocata")), null, bounty.firstAppearance());
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
