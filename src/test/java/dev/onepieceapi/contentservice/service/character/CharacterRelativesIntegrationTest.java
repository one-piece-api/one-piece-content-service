package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import dev.onepieceapi.contentservice.domain.character.RelativeOf;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
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
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.REJECTED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.ADMIN;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.PUBLISHER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The relatives of a character (implementation plan of the Character, C12, D13), against
 * a real PostgreSQL (Testcontainers): kept in their order with their kind; the save
 * refusing the character itself, a repeat, a character not linkable and a pair already
 * linked by the other one; what a character needs to go online with them and what the
 * retirement of a relative owes the characters naming it; the links as the other side
 * reads them, with the inverse kind. Whoever breaks a rule is refused with the reason,
 * even an administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterRelativesIntegrationTest {

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
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entityManager;

	private CharacterFixtures fixtures;

	private CharacterService service;

	private UUID garp;

	private UUID dragon;

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
		this.service = this.fixtures.service();
		this.garp = this.fixtures.character(PUBLISHED, this.fixtures.complete("Garp"));
		this.dragon = this.fixtures.character(PUBLISHED, this.fixtures.complete("Dragon"));
		stored();
	}

	// ---- Saving

	@Test
	void aNewCharacterKeepsItsRelativesInTheirOrderWithTheirKind() {
		var written = withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT),
				link(this.dragon, RelativeKind.PARENT));

		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().relatives())
			.containsExactly(link(this.garp, RelativeKind.GRANDPARENT), link(this.dragon, RelativeKind.PARENT));
	}

	@Test
	void reorderingAndShorteningTheRelativesOfADraftKeepsTheNewList() {
		var first = link(this.garp, RelativeKind.GRANDPARENT);
		var second = link(this.dragon, RelativeKind.PARENT);
		var written = withRelatives("Luffy", first, second);
		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		this.service.edit(EDITOR, CHOPPER, content.id(), 1,
				written.toBuilder().relatives(List.of(second, first)).build());
		stored();
		// What the commit would check: the relatives, swapped row by row, end up
		// distinct.
		this.jdbc.execute("set constraints uq_character_version_relative immediate");
		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().relatives())
			.containsExactly(second, first);

		this.jdbc.execute("set constraints uq_character_version_relative deferred");
		this.service.edit(EDITOR, CHOPPER, content.id(), 1, written.toBuilder().relatives(List.of(first)).build());
		stored();
		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().relatives())
			.containsExactly(first);
	}

	@Test
	void aNewVersionStartsFromItsBaseWithEveryRelativeInItsOrder() {
		var body = withRelatives("Luffy", link(this.dragon, RelativeKind.PARENT),
				link(this.garp, RelativeKind.GRANDPARENT));
		UUID luffy = this.fixtures.character(PUBLISHED, body);
		stored();

		this.service.openNewVersion(EDITOR, CHOPPER, luffy, 1);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, luffy, 2).version().body()).isEqualTo(body);
	}

	@Test
	void aCharacterIsNotItsOwnRelativeByTheIndexOfItsEntry() {
		var content = this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Luffy"));
		stored();
		var written = withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT),
				link(content.id(), RelativeKind.SIBLING));

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.edit(EDITOR, CHOPPER, content.id(), 1, written));

		assertThat(fieldsOf(refused)).containsExactly("relatives[1].relative");
	}

	@Test
	void aRelativeNamedTwiceIsRefusedByTheIndexOfItsRepeat() {
		var written = withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT),
				link(this.dragon, RelativeKind.PARENT), link(this.garp, RelativeKind.COUSIN));

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("relatives[2].relative");
	}

	@Test
	void aRelativeNothingApprovedOfIsRefusedByTheIndexOfItsEntry() {
		UUID draft = this.fixtures.character(DRAFT, this.fixtures.complete("Ace"));
		stored();
		var written = withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT),
				link(draft, RelativeKind.SWORN_SIBLING), link(UUID.randomUUID(), RelativeKind.COUSIN));

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("relatives[1].relative", "relatives[2].relative");
	}

	@Test
	void aPairTheOnlineVersionOfTheOtherAlreadyLinksIsRefusedOnSave() {
		UUID luffy = this.fixtures.character(PUBLISHED,
				withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT)));
		stored();
		this.service.openNewVersion(EDITOR, CHOPPER, this.garp, 1);
		stored();
		var written = withRelatives("Garp", link(luffy, RelativeKind.GRANDCHILD));

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.edit(EDITOR, CHOPPER, this.garp, 2, written));

		assertThat(fieldsOf(refused)).containsExactly("relatives[0].relative");
	}

	@Test
	void aPairAVersionOfTheOtherStillOpenAlreadyLinksIsRefusedOnSaveWhateverItsStatus() {
		for (VersionStatus status : List.of(DRAFT, IN_REVIEW, REJECTED, READY_TO_PUBLISH)) {
			var newcomer = this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Newcomer " + status));
			UUID holder = this.fixtures.character(PUBLISHED, this.fixtures.complete("Holder " + status));
			this.fixtures.characterVersion(holder, 2, status,
					withRelatives("Holder " + status, link(newcomer.id(), RelativeKind.COUSIN)));
			stored();
			var written = withRelatives("Newcomer " + status, link(holder, RelativeKind.COUSIN));

			var refused = catchThrowableOfType(ValueInvalidException.class,
					() -> this.service.edit(EDITOR, CHOPPER, newcomer.id(), 1, written));

			assertThat(fieldsOf(refused)).as(status.name()).containsExactly("relatives[0].relative");
		}
	}

	@Test
	void aPairOnlyAVersionThatLeftTheWorkflowLinkedIsFree() {
		for (VersionStatus status : List.of(SUPERSEDED, RETIRED, ARCHIVED)) {
			var newcomer = this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Newcomer " + status));
			UUID holder = this.fixtures.character(status,
					withRelatives("Holder " + status, link(newcomer.id(), RelativeKind.COUSIN)));
			stored();
			var written = withRelatives("Newcomer " + status, link(holder, RelativeKind.COUSIN));

			var saved = this.service.edit(EDITOR, CHOPPER, newcomer.id(), 1, written);

			assertThat(saved.body().relatives()).as(status.name()).hasSize(1);
		}
	}

	@Test
	void theDatabaseRefusesARelativeThatIsNoCharacter() {
		UUID ningen = this.fixtures.species(PUBLISHED, "Ningen");
		this.fixtures.character(DRAFT, withRelatives("Luffy", link(ningen, RelativeKind.PARENT)));

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_character_version_relative");
	}

	// ---- Going online

	@Test
	void aCharacterGoesOnlineWithItsRelativesWhileEveryOneIsOnline() {
		UUID luffy = this.fixtures.character(READY_TO_PUBLISH, withRelatives("Luffy",
				link(this.garp, RelativeKind.GRANDPARENT), link(this.dragon, RelativeKind.PARENT)));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, luffy, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aCharacterDoesNotGoOnlineWhileARelativeIsNotNamingItsIndexEvenForAnAdministrator() {
		UUID ace = this.fixtures.character(ARCHIVED, this.fixtures.complete("Ace"));
		UUID luffy = this.fixtures.character(READY_TO_PUBLISH, withRelatives("Luffy",
				link(this.garp, RelativeKind.GRANDPARENT), link(ace, RelativeKind.SWORN_SIBLING)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, luffy, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("relativesIndex", 1, "relativeId", ace, "relativeRomaji", "Ace"));
	}

	@Test
	void aPairTheOtherOnlineVersionAlreadyLinksDoesNotGoOnlineEvenForAnAdministrator() {
		UUID luffy = this.fixtures.character(PUBLISHED,
				withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT)));
		this.fixtures.characterVersion(this.garp, 2, READY_TO_PUBLISH,
				withRelatives("Garp", link(this.dragon, RelativeKind.PARENT), link(luffy, RelativeKind.GRANDCHILD)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, this.garp, 2));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATIVE_ALREADY_LINKED);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("relativesIndex", 1, "relativeId", luffy, "relativeRomaji", "Luffy"));
	}

	@Test
	void theBlockIsShownInAdvanceAsEveryOther() {
		UUID luffy = this.fixtures.character(PUBLISHED,
				withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT)));
		this.fixtures.characterVersion(this.garp, 2, READY_TO_PUBLISH,
				withRelatives("Garp", link(luffy, RelativeKind.GRANDCHILD)));
		stored();

		var blocked = this.service.getVersion(ADMIN, CHOPPER, this.garp, 2).blockedActions();

		assertThat(blocked).extracting(BlockedAction::action, BlockedAction::reason)
			.containsExactly(tuple(VersionAction.PUBLISH, BlockReason.RELATIVE_ALREADY_LINKED));
	}

	@Test
	void aCharacterIsNotBlockedByItsOwnVersionOnline() {
		var body = withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT));
		UUID luffy = this.fixtures.character(PUBLISHED, body);
		this.fixtures.characterVersion(luffy, 2, READY_TO_PUBLISH, body.toBuilder().japaneseName("x").build());
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, luffy, 2).version().status()).isEqualTo(PUBLISHED);
	}

	// ---- What a relative owes the characters naming it

	@Test
	void aCharacterIsNotRetiredWhileOnlineCharactersNameItAsARelativeEvenForAnAdministrator() {
		UUID luffy = this.fixtures.character(PUBLISHED,
				withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT)));
		stored();

		var blocked = this.service.getVersion(ADMIN, CHOPPER, this.garp, 1).blockedActions();

		assertThat(blocked).extracting(BlockedAction::action, BlockedAction::reason)
			.contains(tuple(VersionAction.RETIRE, BlockReason.ONLINE_ITEMS_LINKED));
		assertThat(this.characterRepository.countOnlineWithRelative(this.garp)).isEqualTo(1);
		assertThat(this.characterRepository.findOnlineWithRelative(this.garp, Pageable.ofSize(5)))
			.extracting(version -> version.getContentId())
			.containsExactly(luffy);
	}

	@Test
	void theCharacterNamingTheOtherAsARelativeIsRetiredFreely() {
		this.fixtures.character(PUBLISHED, withRelatives("Luffy", link(this.garp, RelativeKind.GRANDPARENT)));
		stored();

		assertThat(this.service.getVersion(ADMIN, CHOPPER, this.dragon, 1).blockedActions())
			.extracting(BlockedAction::reason)
			.doesNotContain(BlockReason.ONLINE_ITEMS_LINKED);
	}

	// ---- As the other side reads them

	@Test
	void eachSideReadsTheLinkThroughTheInverseKindByRomajiAndNeverFromADraft() {
		UUID luffy = this.fixtures.character(PUBLISHED, withRelatives("Luffy",
				link(this.garp, RelativeKind.GRANDPARENT), link(this.dragon, RelativeKind.PARENT)));
		UUID ace = this.fixtures.character(PUBLISHED, withRelatives("Ace", link(luffy, RelativeKind.SWORN_SIBLING)));
		this.fixtures.character(DRAFT, withRelatives("Sabo", link(luffy, RelativeKind.SWORN_SIBLING)));
		stored();
		var links = new CharacterLinks(this.characterRepository, Clock.fixed(CharacterFixtures.NOW, ZoneOffset.UTC));

		assertThat(links.relativesOf(this.garp)).extracting(RelativeOf::kind, RelativeOf::characterId)
			.containsExactly(tuple(RelativeKind.GRANDCHILD, luffy));
		assertThat(links.relativesOf(this.dragon)).extracting(RelativeOf::kind).containsExactly(RelativeKind.CHILD);
		assertThat(links.relativesOf(luffy)).extracting(RelativeOf::kind, RelativeOf::characterId)
			.containsExactly(tuple(RelativeKind.SWORN_SIBLING, ace));
		assertThat(links.relativesOf(ace)).isEmpty();
	}

	private Character withRelatives(String romaji, Relative... relatives) {
		return this.fixtures.complete(romaji).toBuilder().relatives(List.of(relatives)).build();
	}

	private static Relative link(UUID relative, RelativeKind kind) {
		return new Relative(relative, kind);
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
