package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionTranslationRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeItemRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.repository.WorkingRevisionRepository;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionStatus;
import dev.onepieceapi.contentservice.service.exception.CannotDeletePublishedItemException;
import dev.onepieceapi.contentservice.service.exception.ContentVersionNotFoundException;
import dev.onepieceapi.contentservice.service.exception.DuplicateContentException;
import dev.onepieceapi.contentservice.service.exception.EncyclopediaItemNotFoundException;
import dev.onepieceapi.contentservice.service.exception.IdenticalToExistingVersionException;
import dev.onepieceapi.contentservice.service.exception.IncompleteContentException;
import dev.onepieceapi.contentservice.service.exception.InvalidStatusTransitionException;
import dev.onepieceapi.contentservice.service.exception.MissingRejectionReasonException;
import dev.onepieceapi.contentservice.service.exception.NotClaimantException;
import dev.onepieceapi.contentservice.service.exception.ReviewAlreadyClaimedException;
import dev.onepieceapi.contentservice.service.exception.ReviewSlotOccupiedException;
import dev.onepieceapi.contentservice.service.exception.UnknownLanguageException;
import dev.onepieceapi.contentservice.service.exception.WorkingRevisionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.ContentValidator;
import dev.onepieceapi.contentservice.web.dto.TranslationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Runs UF-CNT-01/02 against a real PostgreSQL (Testcontainers), exercising the Flyway
 * migrations and JPA mapping together - see docs/implementation-plan-content.md Step 1's
 * QA acceptance: draft isolation is verified with two real accounts, not assumed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
// @DataJpaTest's own auto-configuration list doesn't include Flyway (it normally assumes
// Hibernate generates the schema) - explicitly pulled in here since this schema is
// Flyway-managed and spring.jpa.hibernate.ddl-auto=validate needs it to already exist.
// Same
// pattern as one-piece-user-service's JpaAuditLogAdapterTest.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeServiceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private DevilFruitTypeItemRepository itemRepository;

	@Autowired
	private WorkingRevisionRepository workingRevisionRepository;

	@Autowired
	private TranslationRepository translationRepository;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private ContentVersionRepository contentVersionRepository;

	@Autowired
	private ContentVersionTranslationRepository contentVersionTranslationRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private DevilFruitTypeService service;

	private final UUID editorA = UUID.randomUUID();

	private final UUID editorB = UUID.randomUUID();

	private final UUID reviewerA = UUID.randomUUID();

	private final UUID reviewerB = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var contentValidator = new ContentValidator(this.translationRepository, this.languageRepository,
				this.workingRevisionRepository, this.contentVersionRepository,
				this.contentVersionTranslationRepository);
		this.service = new DevilFruitTypeService(this.itemRepository, this.workingRevisionRepository,
				this.translationRepository, this.contentVersionRepository, this.contentVersionTranslationRepository,
				contentValidator, auditLogService, clock);
	}

	@Test
	void aNewDraftIsEmptyAndOwnedByItsCreator() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThat(revision.getAuthorId()).isEqualTo(this.editorA);
		assertThat(revision.getStatus().name()).isEqualTo("DRAFT");
		assertThat(revision.getRomaji()).isNull();
	}

	@Test
	void editingSavesRomajiAndTranslationsForEveryLanguageProvided() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		var updated = this.service.updateDraft(revision.getId(), this.editorA, "editor-a@onepiece.local", "Paramishia",
				Map.of("it", new TranslationRequest("Paramecia", "Descrizione IT"), "en",
						new TranslationRequest("", "")));

		assertThat(updated.getRomaji()).isEqualTo("Paramishia");
		var translations = this.service.translationsOf(updated.getId());
		assertThat(translations).hasSize(2);
	}

	@Test
	void editingRejectsAnUnknownLanguageCode() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.updateDraft(revision.getId(), this.editorA, "editor-a@onepiece.local",
				"Paramishia", Map.of("fr", new TranslationRequest("Paramécie", "..."))))
			.isInstanceOf(UnknownLanguageException.class);
	}

	@Test
	void aDraftIsInvisibleToEveryAuthorButItsOwn() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThat(this.service.listOwnDrafts(this.editorA)).extracting(r -> r.getId())
			.containsExactly(revision.getId());
		assertThat(this.service.listOwnDrafts(this.editorB)).isEmpty();

		assertThatThrownBy(() -> this.service.getOwnDraft(revision.getId(), this.editorB))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
		assertThatThrownBy(() -> this.service.updateDraft(revision.getId(), this.editorB, "editor-b@onepiece.local",
				"Hijack", Map.of()))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
	}

	@Test
	void gettingAnUnknownWorkingRevisionFails() {
		assertThatThrownBy(() -> this.service.getOwnDraft(UUID.randomUUID(), this.editorA))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
	}

	@Test
	void submittingAnIncompleteDraftListsWhatIsMissing() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(
				() -> this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local"))
			.isInstanceOf(IncompleteContentException.class);
	}

	@Test
	void submittingACompleteDraftMovesItToInReview() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");

		var submitted = this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local");

		assertThat(submitted.getStatus()).isEqualTo(WorkingRevisionStatus.IN_REVIEW);
	}

	@Test
	void submittingWhileASiblingOfTheSameItemIsAlreadyInReviewFails() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");
		// Simulates a second author's own working revision on the same item (only
		// reachable through the real API from Step 6 onward - constructed directly here,
		// same as one-piece-user-service's own precedent for testing an invariant ahead
		// of the flow that will later produce it).
		var now = this.clock().instant();
		this.workingRevisionRepository.save(new WorkingRevisionEntity(UUID.randomUUID(), revision.getItemId(),
				this.editorB, "editor-b@onepiece.local", WorkingRevisionStatus.IN_REVIEW, now));

		assertThatThrownBy(
				() -> this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local"))
			.isInstanceOf(ReviewSlotOccupiedException.class);
	}

	@Test
	void submittingWhileASiblingOfTheSameItemIsAlreadyReviewedSucceeds() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");
		var now = this.clock().instant();
		this.workingRevisionRepository.save(new WorkingRevisionEntity(UUID.randomUUID(), revision.getItemId(),
				this.editorB, "editor-b@onepiece.local", WorkingRevisionStatus.REVIEWED, now));

		var submitted = this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local");

		assertThat(submitted.getStatus()).isEqualTo(WorkingRevisionStatus.IN_REVIEW);
	}

	@Test
	void submittingRomajiAlreadyReservedByAnotherItemFails() {
		submittedDraft(this.editorA, "editor-a@onepiece.local");
		var revisionB = this.service.createDraft(this.editorB, "editor-b@onepiece.local");
		this.service.updateDraft(revisionB.getId(), this.editorB, "editor-b@onepiece.local", "Paramishia",
				Map.of("it", new TranslationRequest("Zoan", "Descrizione IT"), "en",
						new TranslationRequest("Zoan", "EN description")));

		assertThatThrownBy(
				() -> this.service.submitForReview(revisionB.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(DuplicateContentException.class);
	}

	@Test
	void submittingANameAlreadyReservedByAnotherItemFails() {
		submittedDraft(this.editorA, "editor-a@onepiece.local");
		var revisionB = this.service.createDraft(this.editorB, "editor-b@onepiece.local");
		this.service.updateDraft(revisionB.getId(), this.editorB, "editor-b@onepiece.local", "Zoiashia",
				Map.of("it", new TranslationRequest("Paramecia", "Descrizione IT"), "en",
						new TranslationRequest("Zoan", "EN description")));

		assertThatThrownBy(
				() -> this.service.submitForReview(revisionB.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(DuplicateContentException.class);
	}

	@Test
	void submittingContentMatchingAnotherAuthorsStillPrivateDraftSucceeds() {
		// 7.5/3.3: a DRAFT never submitted by anyone reserves nothing - checking against
		// it would leak that another author's invisible draft exists.
		completeDraft(this.editorA, "editor-a@onepiece.local");
		var revisionB = completeDraft(this.editorB, "editor-b@onepiece.local");

		var submitted = this.service.submitForReview(revisionB.getId(), this.editorB, "editor-b@onepiece.local");

		assertThat(submitted.getStatus()).isEqualTo(WorkingRevisionStatus.IN_REVIEW);
	}

	@Test
	void submittingContentMatchingARetiredItemStillFails() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		this.service.retire(published.getItemId(), this.reviewerA, "reviewer-a@onepiece.local");
		var revisionB = completeDraft(this.editorB, "editor-b@onepiece.local");

		assertThatThrownBy(
				() -> this.service.submitForReview(revisionB.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(DuplicateContentException.class);
	}

	@Test
	void resubmittingTheSameItemsOwnNameAndRomajiIsNeverACollision() {
		// 3.3: editing/resubmitting your own item's own identity replaces a version, it
		// never collides with itself. The description is tweaked (not the name/romaji) so
		// this stays distinct from requireDifferentFromExistingVersions' own "must
		// actually
		// change something" rule below - this test is about the *other* check.
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var newDraft = this.service.editPublishedItem(published.getItemId(), this.editorB, "editor-b@onepiece.local");
		this.service.updateDraft(newDraft.getId(), this.editorB, "editor-b@onepiece.local", newDraft.getRomaji(),
				Map.of("it", new TranslationRequest("Paramecia", "Descrizione IT aggiornata"), "en",
						new TranslationRequest("Paramecia", "EN description")));

		var resubmitted = this.service.submitForReview(newDraft.getId(), this.editorB, "editor-b@onepiece.local");

		assertThat(resubmitted.getStatus()).isEqualTo(WorkingRevisionStatus.IN_REVIEW);
	}

	@Test
	void resubmittingAPublishedItemUnchangedFails() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var newDraft = this.service.editPublishedItem(published.getItemId(), this.editorB, "editor-b@onepiece.local");

		assertThatThrownBy(
				() -> this.service.submitForReview(newDraft.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(IdenticalToExistingVersionException.class);
	}

	@Test
	void resubmittingContentMatchingAnOlderNonLiveVersionAlsoFails() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = approved.getItemId();
		// v2, with different content, becomes live - v1 ("Paramecia"/"Paramishia") is no
		// longer live, but still part of the item's history.
		var v2Draft = this.service.editPublishedItem(itemId, this.editorB, "editor-b@onepiece.local");
		this.service.updateDraft(v2Draft.getId(), this.editorB, "editor-b@onepiece.local", "Zoiashia",
				Map.of("it", new TranslationRequest("Zoan", "Descrizione IT v2"), "en",
						new TranslationRequest("Zoan", "EN description v2")));
		var v2Submitted = this.service.submitForReview(v2Draft.getId(), this.editorB, "editor-b@onepiece.local");
		this.service.claim(v2Submitted.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var v2Approved = this.service.approve(v2Submitted.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		this.service.publish(v2Approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		// A third draft reverts back to v1's exact content - still a collision, even
		// though v1 is no longer the live version.
		var v3Draft = this.service.editPublishedItem(itemId, this.editorA, "editor-a@onepiece.local");
		this.service.updateDraft(v3Draft.getId(), this.editorA, "editor-a@onepiece.local", "Paramishia",
				Map.of("it", new TranslationRequest("Paramecia", "Descrizione IT"), "en",
						new TranslationRequest("Paramecia", "EN description")));

		assertThatThrownBy(() -> this.service.submitForReview(v3Draft.getId(), this.editorA, "editor-a@onepiece.local"))
			.isInstanceOf(IdenticalToExistingVersionException.class);
	}

	@Test
	void submittingSomethingNotInDraftFails() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");
		this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(
				() -> this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local"))
			.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void withdrawingAnInReviewDraftReturnsItToDraft() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");
		this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local");

		var withdrawn = this.service.withdrawToDraft(revision.getId(), this.editorA, "editor-a@onepiece.local");

		assertThat(withdrawn.getStatus()).isEqualTo(WorkingRevisionStatus.DRAFT);
	}

	@Test
	void withdrawingSomethingNotInReviewFails() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(
				() -> this.service.withdrawToDraft(revision.getId(), this.editorA, "editor-a@onepiece.local"))
			.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void submitAndWithdrawAreOwnershipChecked() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(
				() -> this.service.submitForReview(revision.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(WorkingRevisionNotFoundException.class);

		this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local");
		assertThatThrownBy(
				() -> this.service.withdrawToDraft(revision.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
	}

	@Test
	void claimingAnUnclaimedInReviewRevisionSucceeds() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");

		var claimed = this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThat(claimed.getClaimedBy()).isEqualTo(this.reviewerA);
	}

	@Test
	void claimingAnAlreadyClaimedRevisionFails() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThatThrownBy(() -> this.service.claim(revision.getId(), this.reviewerB, "reviewer-b@onepiece.local"))
			.isInstanceOf(ReviewAlreadyClaimedException.class);
	}

	@Test
	void claimingSomethingNotInReviewFails() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local"))
			.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void releasingMakesItClaimableAgainByAnyReviewer() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		this.service.release(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var reclaimed = this.service.claim(revision.getId(), this.reviewerB, "reviewer-b@onepiece.local");

		assertThat(reclaimed.getClaimedBy()).isEqualTo(this.reviewerB);
	}

	@Test
	void releasingAndApprovingAndRejectingRequireBeingTheCurrentClaimant() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThatThrownBy(() -> this.service.release(revision.getId(), this.reviewerB, "reviewer-b@onepiece.local"))
			.isInstanceOf(NotClaimantException.class);
		assertThatThrownBy(() -> this.service.approve(revision.getId(), this.reviewerB, "reviewer-b@onepiece.local"))
			.isInstanceOf(NotClaimantException.class);
		assertThatThrownBy(() -> this.service.reject(revision.getId(), this.reviewerB, "reviewer-b@onepiece.local",
				"Not good enough"))
			.isInstanceOf(NotClaimantException.class);
	}

	@Test
	void approvingByTheClaimantMovesItToReviewedAndClearsTheClaim() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var approved = this.service.approve(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThat(approved.getStatus()).isEqualTo(WorkingRevisionStatus.REVIEWED);
		assertThat(approved.getClaimedBy()).isNull();
	}

	@Test
	void approvingSupersedesAnExistingReviewedSiblingOfTheSameItem() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		var now = this.clock().instant();
		var sibling = this.workingRevisionRepository.save(new WorkingRevisionEntity(UUID.randomUUID(),
				revision.getItemId(), this.editorB, "editor-b@onepiece.local", WorkingRevisionStatus.REVIEWED, now));
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var approved = this.service.approve(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThat(approved.getStatus()).isEqualTo(WorkingRevisionStatus.REVIEWED);
		var supersededSibling = this.workingRevisionRepository.findById(sibling.getId()).orElseThrow();
		assertThat(supersededSibling.getStatus()).isEqualTo(WorkingRevisionStatus.SUPERSEDED);
	}

	@Test
	void rejectingWithoutAReasonFails() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThatThrownBy(
				() -> this.service.reject(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local", " "))
			.isInstanceOf(MissingRejectionReasonException.class);
	}

	@Test
	void rejectingReturnsItToDraftWithTheReasonVisibleAndClearsTheClaim() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var rejected = this.service.reject(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local",
				"Romaji is misspelled");

		assertThat(rejected.getStatus()).isEqualTo(WorkingRevisionStatus.DRAFT);
		assertThat(rejected.getRejectionReason()).isEqualTo("Romaji is misspelled");
		assertThat(rejected.getClaimedBy()).isNull();
	}

	@Test
	void withdrawingAClaimedRevisionFails() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThatThrownBy(
				() -> this.service.withdrawToDraft(revision.getId(), this.editorA, "editor-a@onepiece.local"))
			.isInstanceOf(ReviewAlreadyClaimedException.class);
	}

	@Test
	void withdrawingSucceedsOnceTheClaimIsReleased() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		this.service.release(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var withdrawn = this.service.withdrawToDraft(revision.getId(), this.editorA, "editor-a@onepiece.local");

		assertThat(withdrawn.getStatus()).isEqualTo(WorkingRevisionStatus.DRAFT);
	}

	@Test
	void deletingAnOwnNeverPublishedDraftRemovesItAndItsTranslations() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");

		this.service.deleteDraft(revision.getId(), this.editorA, "editor-a@onepiece.local");

		assertThat(this.workingRevisionRepository.findById(revision.getId())).isEmpty();
		assertThat(this.translationRepository.findByIdWorkingRevisionId(revision.getId())).isEmpty();
	}

	@Test
	void deletingAnotherAuthorsDraftFails() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.deleteDraft(revision.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
	}

	@Test
	void deletingAWorkingRevisionOfAnItemWithPublishedHistoryFails() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var laterDraft = this.service.editPublishedItem(published.getItemId(), this.editorB, "editor-b@onepiece.local");

		assertThatThrownBy(() -> this.service.deleteDraft(laterDraft.getId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(CannotDeletePublishedItemException.class);
	}

	@Test
	void everPublishedReflectsTheItemsVersionHistoryRegardlessOfWorkingRevisionStatus() {
		var revision = completeDraft(this.editorA, "editor-a@onepiece.local");
		assertThat(this.service.everPublished(revision.getItemId())).isFalse();

		var approved = approvedCandidate(this.editorB, "editor-b@onepiece.local");
		this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThat(this.service.everPublished(approved.getItemId())).isTrue();
	}

	@Test
	void editingSomethingNotInDraftFails() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.updateDraft(revision.getId(), this.editorA, "editor-a@onepiece.local",
				"Changed", Map.of()))
			.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void reviewQueueListsOnlyInReviewRevisionsAcrossEveryAuthor() {
		completeDraft(this.editorA, "editor-a@onepiece.local");
		var queued = submittedDraft(this.editorB, "editor-b@onepiece.local");

		var queue = this.service.reviewQueue();

		assertThat(queue).extracting(r -> r.getId()).containsExactly(queued.getId());
	}

	@Test
	void publishingAnApprovedCandidateCreatesTheFirstVersionAndSetsItLive() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");

		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThat(published.getStatus()).isEqualTo(WorkingRevisionStatus.PUBLISHED);
		var item = this.itemRepository.findById(approved.getItemId()).orElseThrow();
		assertThat(item.getLiveVersionId()).isNotNull();
		var version = this.contentVersionRepository.findById(item.getLiveVersionId()).orElseThrow();
		assertThat(version.getSequenceNumber()).isEqualTo(1);
		assertThat(version.getRomaji()).isEqualTo("Paramishia");
		assertThat(this.contentVersionTranslationRepository.findByIdContentVersionId(version.getId())).hasSize(2);
	}

	@Test
	void publishingSomethingNotReviewedFails() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.publish(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local"))
			.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void publishingASecondVersionOfTheSameItemIncrementsTheSequenceNumber() {
		var approvedA = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		this.service.publish(approvedA.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var revisionB = independentReviewedSibling(approvedA.getItemId(), this.editorB, "editor-b@onepiece.local");

		var published = this.service.publish(revisionB.getId(), this.reviewerB, "reviewer-b@onepiece.local");

		assertThat(published.getStatus()).isEqualTo(WorkingRevisionStatus.PUBLISHED);
		var item = this.itemRepository.findById(approvedA.getItemId()).orElseThrow();
		var version = this.contentVersionRepository.findById(item.getLiveVersionId()).orElseThrow();
		assertThat(version.getSequenceNumber()).isEqualTo(2);
	}

	@Test
	void listEncyclopediaPrefersAReviewedCandidateOverAnAlreadyPublishedSiblingOfTheSameItem() {
		var approvedA = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		this.service.publish(approvedA.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var revisionB = independentReviewedSibling(approvedA.getItemId(), this.editorB, "editor-b@onepiece.local");

		var encyclopedia = this.service.listEncyclopedia();

		assertThat(encyclopedia).hasSize(1);
		assertThat(encyclopedia.get(0)).isInstanceOf(EncyclopediaEntry.ReviewedCandidate.class);
		var reviewed = (EncyclopediaEntry.ReviewedCandidate) encyclopedia.get(0);
		assertThat(reviewed.revision().getId()).isEqualTo(revisionB.getId());
	}

	@Test
	void listEncyclopediaShowsAPublishedItemWithNoReviewedCandidate() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var encyclopedia = this.service.listEncyclopedia();

		assertThat(encyclopedia).hasSize(1);
		assertThat(encyclopedia.get(0)).isInstanceOf(EncyclopediaEntry.PublishedItem.class);
	}

	@Test
	void gettingAnEncyclopediaItemReturnsTheLivePublishedVersion() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var entry = this.service.getEncyclopediaItem(approved.getItemId());

		assertThat(entry).isInstanceOf(EncyclopediaEntry.PublishedItem.class);
	}

	@Test
	void gettingAnUnknownEncyclopediaItemFails() {
		assertThatThrownBy(() -> this.service.getEncyclopediaItem(UUID.randomUUID()))
			.isInstanceOf(EncyclopediaItemNotFoundException.class);
	}

	@Test
	void gettingAnEncyclopediaItemForAStillPrivateDraftFails() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.getEncyclopediaItem(revision.getItemId()))
			.isInstanceOf(EncyclopediaItemNotFoundException.class);
	}

	@Test
	void editingAPublishedItemCreatesANewDraftPrefilledFromTheLiveSnapshot() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var newDraft = this.service.editPublishedItem(published.getItemId(), this.editorB, "editor-b@onepiece.local");

		assertThat(newDraft.getId()).isNotEqualTo(published.getId());
		assertThat(newDraft.getItemId()).isEqualTo(published.getItemId());
		assertThat(newDraft.getStatus()).isEqualTo(WorkingRevisionStatus.DRAFT);
		assertThat(newDraft.getAuthorId()).isEqualTo(this.editorB);
		assertThat(newDraft.getRomaji()).isEqualTo("Paramishia");
		var translations = this.service.translationsOf(newDraft.getId());
		assertThat(translations).hasSize(2);
	}

	@Test
	void editingAPublishedItemLeavesTheLiveContentUntouched() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemBefore = this.itemRepository.findById(published.getItemId()).orElseThrow();

		this.service.editPublishedItem(published.getItemId(), this.editorB, "editor-b@onepiece.local");

		var itemAfter = this.itemRepository.findById(published.getItemId()).orElseThrow();
		assertThat(itemAfter.getLiveVersionId()).isEqualTo(itemBefore.getLiveVersionId());
	}

	@Test
	void twoAuthorsCanIndependentlyEditTheSamePublishedItem() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		var draftB = this.service.editPublishedItem(published.getItemId(), this.editorB, "editor-b@onepiece.local");
		var draftC = this.service.editPublishedItem(published.getItemId(), this.editorA, "editor-a@onepiece.local");

		assertThat(draftB.getId()).isNotEqualTo(draftC.getId());
		assertThat(draftB.getItemId()).isEqualTo(draftC.getItemId());
	}

	@Test
	void editingAnItemThatWasNeverPublishedFails() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(
				() -> this.service.editPublishedItem(revision.getItemId(), this.editorB, "editor-b@onepiece.local"))
			.isInstanceOf(EncyclopediaItemNotFoundException.class);
	}

	@Test
	void editingAnUnknownItemFails() {
		assertThatThrownBy(
				() -> this.service.editPublishedItem(UUID.randomUUID(), this.editorA, "editor-a@onepiece.local"))
			.isInstanceOf(EncyclopediaItemNotFoundException.class);
	}

	@Test
	void listVersionsReturnsEveryPublishedSnapshotMostRecentFirstWithTheLiveOneFlagged() {
		var approvedA = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var v1 = this.service.publish(approvedA.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var revisionB = independentReviewedSibling(approvedA.getItemId(), this.editorB, "editor-b@onepiece.local");
		var v2 = this.service.publish(revisionB.getId(), this.reviewerB, "reviewer-b@onepiece.local");

		var versions = this.service.listVersions(v2.getItemId());

		assertThat(versions).hasSize(2);
		var item = this.itemRepository.findById(v2.getItemId()).orElseThrow();
		assertThat(versions.get(0).getSequenceNumber()).isEqualTo(2);
		assertThat(versions.get(0).getId()).isEqualTo(item.getLiveVersionId());
		assertThat(versions.get(1).getSequenceNumber()).isEqualTo(1);
	}

	@Test
	void gettingAVersionReturnsItsFullContent() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();
		var v1Id = this.itemRepository.findById(itemId).orElseThrow().getLiveVersionId();

		var version = this.service.getVersion(itemId, v1Id);
		var translations = this.service.versionTranslationsOf(version.getId());

		assertThat(version.getRomaji()).isEqualTo("Paramishia");
		assertThat(translations).extracting(t -> t.getId().getLanguageCode()).containsExactlyInAnyOrder("it", "en");
	}

	@Test
	void gettingAVersionThatDoesNotBelongToTheItemFails() {
		var approvedA = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var publishedA = this.service.publish(approvedA.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var v1Id = this.itemRepository.findById(publishedA.getItemId()).orElseThrow().getLiveVersionId();
		var otherItemApproved = approvedCandidate(this.editorB, "editor-b@onepiece.local", "Zoiashia", "Zoan");
		var otherItemPublished = this.service.publish(otherItemApproved.getId(), this.reviewerB,
				"reviewer-b@onepiece.local");

		assertThatThrownBy(() -> this.service.getVersion(otherItemPublished.getItemId(), v1Id))
			.isInstanceOf(ContentVersionNotFoundException.class);
	}

	@Test
	void restoringRepointsTheLivePointerWithoutCreatingANewVersionRow() {
		var approvedA = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		this.service.publish(approvedA.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var revisionB = independentReviewedSibling(approvedA.getItemId(), this.editorB, "editor-b@onepiece.local");
		var v2 = this.service.publish(revisionB.getId(), this.reviewerB, "reviewer-b@onepiece.local");
		var itemId = v2.getItemId();
		var v1Id = this.contentVersionRepository.findByItemIdOrderBySequenceNumberDesc(itemId)
			.stream()
			.filter(v -> v.getSequenceNumber() == 1)
			.findFirst()
			.orElseThrow()
			.getId();

		var restored = this.service.restore(itemId, v1Id, this.reviewerA, "reviewer-a@onepiece.local");

		assertThat(restored.getId()).isEqualTo(v1Id);
		var item = this.itemRepository.findById(itemId).orElseThrow();
		assertThat(item.getLiveVersionId()).isEqualTo(v1Id);
		assertThat(this.contentVersionRepository.findByItemIdOrderBySequenceNumberDesc(itemId)).hasSize(2);
	}

	@Test
	void restoringAVersionThatDoesNotBelongToTheItemFails() {
		var approvedA = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approvedA.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemAVersionId = this.itemRepository.findById(published.getItemId()).orElseThrow().getLiveVersionId();
		var otherItemApproved = approvedCandidate(this.editorB, "editor-b@onepiece.local", "Zoiashia", "Zoan");
		var otherItemPublished = this.service.publish(otherItemApproved.getId(), this.reviewerB,
				"reviewer-b@onepiece.local");
		var otherItemId = otherItemPublished.getItemId();

		assertThatThrownBy(
				() -> this.service.restore(otherItemId, itemAVersionId, this.reviewerA, "reviewer-a@onepiece.local"))
			.isInstanceOf(ContentVersionNotFoundException.class);
	}

	@Test
	void restoringAnUnknownVersionFails() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var v1 = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		assertThatThrownBy(() -> this.service.restore(v1.getItemId(), UUID.randomUUID(), this.reviewerA,
				"reviewer-a@onepiece.local"))
			.isInstanceOf(ContentVersionNotFoundException.class);
	}

	@Test
	void retiringClearsTheLivePointerWithoutTouchingHistory() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();

		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");

		var item = this.itemRepository.findById(itemId).orElseThrow();
		assertThat(item.getLiveVersionId()).isNull();
		assertThat(this.contentVersionRepository.findByItemIdOrderBySequenceNumberDesc(itemId)).hasSize(1);
	}

	@Test
	void retiringIsIdempotent() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();
		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");

		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");

		var item = this.itemRepository.findById(itemId).orElseThrow();
		assertThat(item.getLiveVersionId()).isNull();
	}

	@Test
	void retiringAnItemThatWasNeverPublishedFails() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.retire(revision.getItemId(), this.reviewerA, "reviewer-a@onepiece.local"))
			.isInstanceOf(EncyclopediaItemNotFoundException.class);
	}

	@Test
	void retiringAnUnknownItemFails() {
		assertThatThrownBy(() -> this.service.retire(UUID.randomUUID(), this.reviewerA, "reviewer-a@onepiece.local"))
			.isInstanceOf(EncyclopediaItemNotFoundException.class);
	}

	@Test
	void aRetiredItemShowsInTheEncyclopediaWithItsLastLiveContent() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();
		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");

		var entry = this.service.getEncyclopediaItem(itemId);

		assertThat(entry).isInstanceOf(EncyclopediaEntry.RetiredItem.class);
		var retired = (EncyclopediaEntry.RetiredItem) entry;
		assertThat(retired.lastVersion().getRomaji()).isEqualTo("Paramishia");

		var encyclopedia = this.service.listEncyclopedia();
		assertThat(encyclopedia).hasSize(1).first().isInstanceOf(EncyclopediaEntry.RetiredItem.class);
	}

	@Test
	void aReviewedSiblingTakesPriorityOverARetiredItemInTheEncyclopedia() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();
		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");
		independentReviewedSibling(itemId, this.editorB, "editor-b@onepiece.local");

		var entry = this.service.getEncyclopediaItem(itemId);

		assertThat(entry).isInstanceOf(EncyclopediaEntry.ReviewedCandidate.class);
	}

	@Test
	void editingARetiredItemPrefillsFromItsLastLiveSnapshot() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();
		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");

		var newDraft = this.service.editPublishedItem(itemId, this.editorB, "editor-b@onepiece.local");

		assertThat(newDraft.getItemId()).isEqualTo(itemId);
		assertThat(newDraft.getStatus()).isEqualTo(WorkingRevisionStatus.DRAFT);
		assertThat(newDraft.getRomaji()).isEqualTo("Paramishia");
		assertThat(this.service.translationsOf(newDraft.getId())).hasSize(2);
	}

	@Test
	void aRetiredItemCanReturnLiveViaPublishOfANewlyApprovedCandidate() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();
		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");
		var revisionB = independentReviewedSibling(itemId, this.editorB, "editor-b@onepiece.local");

		this.service.publish(revisionB.getId(), this.reviewerB, "reviewer-b@onepiece.local");

		var item = this.itemRepository.findById(itemId).orElseThrow();
		assertThat(item.getLiveVersionId()).isNotNull();
		assertThat(this.service.getEncyclopediaItem(itemId)).isInstanceOf(EncyclopediaEntry.PublishedItem.class);
	}

	@Test
	void aRetiredItemCanReturnLiveViaRestore() {
		var approved = approvedCandidate(this.editorA, "editor-a@onepiece.local");
		var published = this.service.publish(approved.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		var itemId = published.getItemId();
		var v1Id = this.itemRepository.findById(itemId).orElseThrow().getLiveVersionId();
		this.service.retire(itemId, this.reviewerA, "reviewer-a@onepiece.local");

		this.service.restore(itemId, v1Id, this.reviewerA, "reviewer-a@onepiece.local");

		var item = this.itemRepository.findById(itemId).orElseThrow();
		assertThat(item.getLiveVersionId()).isEqualTo(v1Id);
	}

	/**
	 * A `REVIEWED` working revision, claimed and approved by reviewerA, ready to publish.
	 */
	private WorkingRevisionEntity approvedCandidate(UUID authorId, String authorEmail) {
		var revision = submittedDraft(authorId, authorEmail);
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		return this.service.approve(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");
	}

	/**
	 * Same as {@link #approvedCandidate(UUID, String)}, but for a genuinely separate item
	 * that must coexist with one already reserving {@code completeDraft}'s hardcoded
	 * romaji/name (3.3) - used only where a test needs two independent items both
	 * `REVIEWED`/`PUBLISHED` at once, which the shared content would otherwise collide
	 * on.
	 */
	private WorkingRevisionEntity approvedCandidate(UUID authorId, String authorEmail, String romaji, String name) {
		var revision = this.service.createDraft(authorId, authorEmail);
		var filled = this.service.updateDraft(revision.getId(), authorId, authorEmail, romaji, Map.of("it",
				new TranslationRequest(name, "Descrizione IT"), "en", new TranslationRequest(name, "EN description")));
		var submitted = this.service.submitForReview(filled.getId(), authorId, authorEmail);
		this.service.claim(submitted.getId(), this.reviewerA, "reviewer-a@onepiece.local");
		return this.service.approve(submitted.getId(), this.reviewerA, "reviewer-a@onepiece.local");
	}

	/**
	 * A second, independently-authored `REVIEWED` working revision of an item that
	 * already has one - only reachable through the real API from Step 6 onward,
	 * constructed directly here, same precedent as the supersession tests above.
	 */
	private WorkingRevisionEntity independentReviewedSibling(UUID itemId, UUID authorId, String authorEmail) {
		return this.workingRevisionRepository.save(new WorkingRevisionEntity(UUID.randomUUID(), itemId, authorId,
				authorEmail, WorkingRevisionStatus.REVIEWED, this.clock().instant()));
	}

	/** A submitted, `IN_REVIEW` working revision, ready for claim/approve/reject. */
	private WorkingRevisionEntity submittedDraft(UUID authorId, String authorEmail) {
		var revision = completeDraft(authorId, authorEmail);
		return this.service.submitForReview(revision.getId(), authorId, authorEmail);
	}

	/** A draft filled in for every active language, ready to submit. */
	private WorkingRevisionEntity completeDraft(UUID authorId, String authorEmail) {
		var revision = this.service.createDraft(authorId, authorEmail);
		return this.service.updateDraft(revision.getId(), authorId, authorEmail, "Paramishia",
				Map.of("it", new TranslationRequest("Paramecia", "Descrizione IT"), "en",
						new TranslationRequest("Paramecia", "EN description")));
	}

	private Clock clock() {
		return Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
	}

}
