package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.DevilFruitTypeItemRepository;
import dev.onepieceapi.contentservice.persistence.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionStatus;
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
		this.service = new DevilFruitTypeService(this.itemRepository, this.workingRevisionRepository,
				this.translationRepository, this.languageRepository, auditLogService, clock);
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
	void withdrawingReleasesAnExistingClaimAutomatically() {
		var revision = submittedDraft(this.editorA, "editor-a@onepiece.local");
		this.service.claim(revision.getId(), this.reviewerA, "reviewer-a@onepiece.local");

		this.service.withdrawToDraft(revision.getId(), this.editorA, "editor-a@onepiece.local");
		var resubmitted = this.service.submitForReview(revision.getId(), this.editorA, "editor-a@onepiece.local");
		var reclaimed = this.service.claim(resubmitted.getId(), this.reviewerB, "reviewer-b@onepiece.local");

		assertThat(reclaimed.getClaimedBy()).isEqualTo(this.reviewerB);
	}

	@Test
	void reviewQueueListsOnlyInReviewRevisionsAcrossEveryAuthor() {
		completeDraft(this.editorA, "editor-a@onepiece.local");
		var queued = submittedDraft(this.editorB, "editor-b@onepiece.local");

		var queue = this.service.reviewQueue();

		assertThat(queue).extracting(r -> r.getId()).containsExactly(queued.getId());
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
