package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_ADMIN;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_PUBLISH;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_READ;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_RETIRE;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_REVIEW;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_WRITE;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.ALLOWED;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.CONFLICT;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.FORBIDDEN;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.APPROVE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.ARCHIVE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.CLAIM;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.DELETE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.EDIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.OPEN_NEW_VERSION;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.PULL_BACK;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RECOVER;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.REJECT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RELEASE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RESTORE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RETIRE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RETURN_TO_DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.SUBMIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transition table of docs/user-flows/content-editorial-workflow.md 4.2, read status
 * by status: what each of the default roles may do to a version written by nami, and how
 * an action they may not perform is refused - then again for luffy, holding
 * {@code content:admin} (2.3).
 */
class TransitionPolicyTest {

	private static final User NAMI = user("nami");

	private static final User CHOPPER = user("chopper");

	private static final User ZORO = user("zoro");

	private static final User LAW = user("law");

	private static final User VIVI = user("vivi");

	private static final Set<Permission> EDITOR = Set.of(CONTENT_READ, CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(CONTENT_READ, CONTENT_REVIEW);

	private static final Set<Permission> PUBLISHER = Set.of(CONTENT_READ, CONTENT_PUBLISH, CONTENT_RETIRE);

	private static final Set<Permission> EDITOR_AND_REVIEWER = Set.of(CONTENT_READ, CONTENT_WRITE, CONTENT_REVIEW);

	private static final User LUFFY = user("luffy");

	private static final Set<Permission> ADMIN = Set.of(CONTENT_READ, CONTENT_WRITE, CONTENT_REVIEW, CONTENT_PUBLISH,
			CONTENT_RETIRE, CONTENT_ADMIN);

	@Test
	void theAuthorOfADraftMayEditDeleteAndSubmitIt() {
		assertThat(allowed(version(DRAFT), NAMI, EDITOR)).containsExactly(EDIT, DELETE, SUBMIT);
	}

	@Test
	void aDraftIsOnlyItsAuthorsToTouch() {
		assertThat(allowed(version(DRAFT), CHOPPER, EDITOR)).isEmpty();
	}

	@Test
	void theAuthorMayPullBackASubmissionNobodyHolds() {
		assertThat(allowed(version(IN_REVIEW), NAMI, EDITOR)).containsExactly(PULL_BACK);
		assertThat(allowed(claimedBy(ZORO), NAMI, EDITOR)).isEmpty();
	}

	@Test
	void aReviewerMayClaimAnUnclaimedVersionSomeoneElseWrote() {
		assertThat(allowed(version(IN_REVIEW), ZORO, REVIEWER)).containsExactly(CLAIM);
	}

	@Test
	void nobodyReviewsTheirOwnVersion() {
		assertThat(allowed(version(IN_REVIEW), NAMI, EDITOR_AND_REVIEWER)).containsExactly(PULL_BACK);
	}

	@Test
	void onlyTheClaimantMayReleaseOrDecide() {
		assertThat(allowed(claimedBy(ZORO), ZORO, REVIEWER)).containsExactly(RELEASE, APPROVE, REJECT);
		assertThat(allowed(claimedBy(ZORO), LAW, EDITOR_AND_REVIEWER)).isEmpty();
	}

	@Test
	void aRejectedVersionCanOnlyBeReturnedToDraftByItsAuthor() {
		assertThat(allowed(version(REJECTED), NAMI, EDITOR)).containsExactly(RETURN_TO_DRAFT);
		assertThat(allowed(version(REJECTED), CHOPPER, EDITOR)).isEmpty();
	}

	@Test
	void aReadyVersionIsPublishedOrArchivedByWhoPublishes() {
		assertThat(allowed(version(READY_TO_PUBLISH), VIVI, PUBLISHER)).containsExactly(PUBLISH, ARCHIVE);
		assertThat(allowed(version(READY_TO_PUBLISH), NAMI, EDITOR)).isEmpty();
		assertThat(allowed(version(READY_TO_PUBLISH), ZORO, REVIEWER)).isEmpty();
	}

	@Test
	void retiringTakesItsOwnPermission() {
		assertThat(allowed(version(PUBLISHED), VIVI, PUBLISHER)).containsExactly(RETIRE);
		assertThat(allowed(version(PUBLISHED), VIVI, Set.of(CONTENT_READ, CONTENT_PUBLISH))).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(names = { "SUPERSEDED", "RETIRED" })
	void aVersionThatWasOnlineMayBeRestoredWhateverElseIsOpen(VersionStatus status) {
		assertThat(allowed(version(status), VIVI, PUBLISHER)).containsExactly(RESTORE);
		assertThat(allowed(version(status), true, VIVI, PUBLISHER)).containsExactly(RESTORE);
	}

	@Test
	void anArchivedVersionIsRecoveredOnlyWhileTheContentHasNoOpenVersion() {
		assertThat(allowed(version(ARCHIVED), VIVI, PUBLISHER)).containsExactly(RECOVER);
		assertThat(allowed(version(ARCHIVED), true, VIVI, PUBLISHER)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(names = { "PUBLISHED", "ARCHIVED", "RETIRED", "SUPERSEDED" })
	void anyEditorMayOpenANewVersionFromAClosedOneWhileNoneIsOpen(VersionStatus status) {
		assertThat(allowed(version(status), CHOPPER, EDITOR)).containsExactly(OPEN_NEW_VERSION);
		assertThat(allowed(version(status), true, CHOPPER, EDITOR)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(VersionStatus.class)
	void readingAloneAllowsNothing(VersionStatus status) {
		assertThat(allowed(version(status), NAMI, Set.of(CONTENT_READ))).isEmpty();
	}

	@Test
	void aRefusalIsAboutTheCallerOrAboutTheState() {
		assertThat(decide(EDIT, version(DRAFT), NAMI, EDITOR)).isEqualTo(ALLOWED);
		// Not theirs to edit, or no permission to write at all: someone else must act.
		assertThat(decide(EDIT, version(DRAFT), CHOPPER, EDITOR)).isEqualTo(FORBIDDEN);
		assertThat(decide(EDIT, version(DRAFT), ZORO, REVIEWER)).isEqualTo(FORBIDDEN);
		// Theirs, but no longer a draft: the version must move first.
		assertThat(decide(EDIT, version(PUBLISHED), NAMI, EDITOR)).isEqualTo(CONFLICT);
	}

	@Test
	void onlyItsAuthorDiscardsADraftAndOnlyWhileItIsOne() {
		assertThat(decide(DELETE, version(DRAFT), NAMI, EDITOR)).isEqualTo(ALLOWED);
		assertThat(decide(DELETE, version(DRAFT), CHOPPER, EDITOR)).isEqualTo(FORBIDDEN);
		// A rejected version must be returned to draft first.
		assertThat(decide(DELETE, version(REJECTED), NAMI, EDITOR)).isEqualTo(CONFLICT);
		assertThat(decide(DELETE, version(PUBLISHED), NAMI, EDITOR)).isEqualTo(CONFLICT);
	}

	@Test
	void whoIsAskingComesBeforeTheStateOfTheVersion() {
		assertThat(decide(EDIT, version(PUBLISHED), CHOPPER, EDITOR)).isEqualTo(FORBIDDEN);
		assertThat(decide(CLAIM, claimedBy(ZORO), NAMI, EDITOR_AND_REVIEWER)).isEqualTo(FORBIDDEN);
	}

	@Test
	void aConditionOnTheStateRefusesAsAConflictAndOneOnTheCallerAsForbidden() {
		assertThat(decide(PULL_BACK, claimedBy(ZORO), NAMI, EDITOR)).isEqualTo(CONFLICT);
		assertThat(decide(CLAIM, claimedBy(ZORO), LAW, EDITOR_AND_REVIEWER)).isEqualTo(CONFLICT);
		assertThat(decide(CLAIM, version(IN_REVIEW), NAMI, EDITOR_AND_REVIEWER)).isEqualTo(FORBIDDEN);
		assertThat(decide(APPROVE, claimedBy(ZORO), LAW, EDITOR_AND_REVIEWER)).isEqualTo(FORBIDDEN);
		var recover = new TransitionContext(version(ARCHIVED), true, VIVI, PUBLISHER);
		assertThat(TransitionPolicy.decide(RECOVER, recover)).isEqualTo(CONFLICT);
	}

	@Test
	void anAdministratorActsOnSomeoneElsesDraftAsItsAuthorWould() {
		assertThat(allowed(version(DRAFT), LUFFY, ADMIN)).containsExactly(EDIT, DELETE, SUBMIT);
		assertThat(overrides(version(DRAFT), LUFFY, ADMIN)).containsExactly(EDIT, DELETE, SUBMIT);
		assertThat(allowed(version(REJECTED), LUFFY, ADMIN)).containsExactly(RETURN_TO_DRAFT);
		assertThat(overrides(version(REJECTED), LUFFY, ADMIN)).containsExactly(RETURN_TO_DRAFT);
	}

	@Test
	void anAdministratorPullsBackSomeoneElsesSubmissionOnlyWhileNobodyHoldsIt() {
		assertThat(allowed(version(IN_REVIEW), LUFFY, ADMIN)).containsExactly(PULL_BACK, CLAIM);
		assertThat(overrides(version(IN_REVIEW), LUFFY, ADMIN)).containsExactly(PULL_BACK);
		assertThat(decide(PULL_BACK, claimedBy(ZORO), LUFFY, ADMIN)).isEqualTo(CONFLICT);
	}

	@Test
	void anAdministratorReleasesSomeoneElsesClaimButNeverDecidesOnIt() {
		assertThat(allowed(claimedBy(ZORO), LUFFY, ADMIN)).containsExactly(RELEASE);
		assertThat(overrides(claimedBy(ZORO), LUFFY, ADMIN)).containsExactly(RELEASE);
		assertThat(decide(APPROVE, claimedBy(ZORO), LUFFY, ADMIN)).isEqualTo(FORBIDDEN);
		assertThat(decide(REJECT, claimedBy(ZORO), LUFFY, ADMIN)).isEqualTo(FORBIDDEN);
	}

	@Test
	void thereMustBeAClaimToRelease() {
		assertThat(decide(RELEASE, version(IN_REVIEW), LUFFY, ADMIN)).isEqualTo(CONFLICT);
		assertThat(decide(RELEASE, version(IN_REVIEW), ZORO, REVIEWER)).isEqualTo(FORBIDDEN);
	}

	@Test
	void anAdministratorReviewsTheirOwnVersionAndDecidesOnceHoldingIt() {
		var ownSubmission = Version.<String>builder().number(1).status(IN_REVIEW).author(LUFFY).build();
		assertThat(allowed(ownSubmission, LUFFY, ADMIN)).containsExactly(PULL_BACK, CLAIM);
		assertThat(overrides(ownSubmission, LUFFY, ADMIN)).containsExactly(CLAIM);

		var ownClaimed = Version.<String>builder().number(1).status(IN_REVIEW).author(LUFFY).claimant(LUFFY).build();
		assertThat(allowed(ownClaimed, LUFFY, ADMIN)).containsExactly(RELEASE, APPROVE, REJECT);
		assertThat(overrides(ownClaimed, LUFFY, ADMIN)).isEmpty();
	}

	@Test
	void theOverrideGrantsNoActionByItself() {
		var adminWithoutReview = Set.of(CONTENT_READ, CONTENT_WRITE, CONTENT_ADMIN);
		var ownSubmission = Version.<String>builder().number(1).status(IN_REVIEW).author(LUFFY).build();
		assertThat(decide(CLAIM, ownSubmission, LUFFY, adminWithoutReview)).isEqualTo(FORBIDDEN);
		assertThat(decide(RELEASE, claimedBy(ZORO), LUFFY, adminWithoutReview)).isEqualTo(FORBIDDEN);
		assertThat(decide(EDIT, version(DRAFT), LUFFY, Set.of(CONTENT_READ, CONTENT_REVIEW, CONTENT_ADMIN)))
			.isEqualTo(FORBIDDEN);
		for (VersionStatus status : VersionStatus.values()) {
			assertThat(allowed(version(status), LUFFY, Set.of(CONTENT_READ, CONTENT_ADMIN))).isEmpty();
		}
	}

	@Test
	void theOverrideLiftsNoConditionOnTheState() {
		assertThat(decide(EDIT, version(PUBLISHED), LUFFY, ADMIN)).isEqualTo(CONFLICT);
		assertThat(decide(DELETE, version(REJECTED), LUFFY, ADMIN)).isEqualTo(CONFLICT);
		assertThat(decide(CLAIM, claimedBy(ZORO), LUFFY, ADMIN)).isEqualTo(CONFLICT);
		var recover = new TransitionContext(version(ARCHIVED), true, LUFFY, ADMIN);
		assertThat(TransitionPolicy.decide(RECOVER, recover)).isEqualTo(CONFLICT);
	}

	@ParameterizedTest
	@EnumSource(VersionStatus.class)
	void withoutTheOverrideNothingIsAnOverrideAndWithItOnlyWhatIsAllowed(VersionStatus status) {
		for (Version<?> version : List.of(version(status), claimedBy(ZORO))) {
			assertThat(overrides(version, LAW, EDITOR_AND_REVIEWER)).isEmpty();
			assertThat(allowed(version, LUFFY, ADMIN)).containsAll(overrides(version, LUFFY, ADMIN));
		}
	}

	@ParameterizedTest
	@EnumSource(VersionStatus.class)
	void anAdministratorsOwnWorkIsNoOverride(VersionStatus status) {
		var own = Version.<String>builder().number(1).status(status).author(LUFFY).build();
		assertThat(overrides(own, LUFFY, ADMIN)).isSubsetOf(CLAIM);
	}

	@ParameterizedTest
	@EnumSource(VersionStatus.class)
	void whatIsOfferedIsExactlyWhatIsDecidedAllowed(VersionStatus status) {
		var everyPermission = EnumSet.allOf(Permission.class);
		for (User caller : List.of(NAMI, ZORO)) {
			var context = new TransitionContext(version(status), false, caller, everyPermission);
			var decidedAllowed = EnumSet.noneOf(VersionAction.class);
			for (VersionAction action : VersionAction.values()) {
				if (TransitionPolicy.decide(action, context) == ALLOWED) {
					decidedAllowed.add(action);
				}
			}

			assertThat(TransitionPolicy.allowedActions(context)).isEqualTo(decidedAllowed);
		}
	}

	@Test
	void everyActionHasARule() {
		var everyPermission = EnumSet.allOf(Permission.class);
		var reachable = EnumSet.noneOf(VersionAction.class);
		for (VersionStatus status : VersionStatus.values()) {
			for (Version<?> version : List.of(version(status), claimedBy(ZORO))) {
				reachable.addAll(allowed(version, NAMI, everyPermission));
				reachable.addAll(allowed(version, ZORO, everyPermission));
			}
		}

		assertThat(reachable).containsExactlyInAnyOrder(VersionAction.values());
	}

	private static Set<VersionAction> allowed(Version<?> version, User caller, Set<Permission> permissions) {
		return allowed(version, false, caller, permissions);
	}

	private static Set<VersionAction> allowed(Version<?> version, boolean contentHasOpenVersion, User caller,
			Set<Permission> permissions) {
		var context = new TransitionContext(version, contentHasOpenVersion, caller, permissions);
		return TransitionPolicy.allowedActions(context);
	}

	private static Set<VersionAction> overrides(Version<?> version, User caller, Set<Permission> permissions) {
		return TransitionPolicy.overrideActions(new TransitionContext(version, false, caller, permissions));
	}

	private static TransitionDecision decide(VersionAction action, Version<?> version, User caller,
			Set<Permission> permissions) {
		return TransitionPolicy.decide(action, new TransitionContext(version, false, caller, permissions));
	}

	/** A version written by nami, held by nobody. */
	private static Version<String> version(VersionStatus status) {
		return Version.<String>builder().number(1).status(status).author(NAMI).build();
	}

	/** A version by nami in review, held by the given reviewer. */
	private static Version<String> claimedBy(User reviewer) {
		return Version.<String>builder().number(1).status(IN_REVIEW).author(NAMI).claimant(reviewer).build();
	}

	private static User user(String username) {
		return new User(UUID.randomUUID(), username, username + "@onepiece.local");
	}

}
