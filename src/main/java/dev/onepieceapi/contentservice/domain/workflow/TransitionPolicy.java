package dev.onepieceapi.contentservice.domain.workflow;

import lombok.experimental.UtilityClass;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_PUBLISH;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_RETIRE;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_REVIEW;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_WRITE;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionCondition.AUTHOR;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionCondition.CLAIMED;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionCondition.CLAIMANT;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionCondition.NOT_AUTHOR;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionCondition.NO_OPEN_VERSION;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionCondition.UNCLAIMED;
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
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;

/**
 * What a caller may do to a version - the transition table of
 * docs/user-flows/content-editorial-workflow.md 4.2, as data: one {@link TransitionRule}
 * per row. An action is allowed when its rule starts from the version's status, the
 * caller holds its permission and every one of its conditions is met - or, on an
 * overridable rule, every condition on the state, for a caller holding
 * {@code content:admin} (2.3).
 */
@UtilityClass
public class TransitionPolicy {

	private static final List<TransitionRule> RULES = List.of(
			new TransitionRule(EDIT, Set.of(DRAFT), CONTENT_WRITE, Set.of(AUTHOR)).overridable(),
			new TransitionRule(DELETE, Set.of(DRAFT), CONTENT_WRITE, Set.of(AUTHOR)).overridable(),
			new TransitionRule(SUBMIT, Set.of(DRAFT), CONTENT_WRITE, Set.of(AUTHOR)).overridable(),
			new TransitionRule(PULL_BACK, Set.of(IN_REVIEW), CONTENT_WRITE, Set.of(AUTHOR, UNCLAIMED)).overridable(),
			new TransitionRule(CLAIM, Set.of(IN_REVIEW), CONTENT_REVIEW, Set.of(NOT_AUTHOR, UNCLAIMED)).overridable(),
			new TransitionRule(RELEASE, Set.of(IN_REVIEW), CONTENT_REVIEW, Set.of(CLAIMED, CLAIMANT)).overridable(),
			// Whoever decides holds the claim, administrator or not (2.3).
			new TransitionRule(APPROVE, Set.of(IN_REVIEW), CONTENT_REVIEW, Set.of(CLAIMANT)),
			new TransitionRule(REJECT, Set.of(IN_REVIEW), CONTENT_REVIEW, Set.of(CLAIMANT)),
			new TransitionRule(RETURN_TO_DRAFT, Set.of(REJECTED), CONTENT_WRITE, Set.of(AUTHOR)).overridable(),
			new TransitionRule(PUBLISH, Set.of(READY_TO_PUBLISH), CONTENT_PUBLISH),
			new TransitionRule(ARCHIVE, Set.of(READY_TO_PUBLISH), CONTENT_PUBLISH),
			new TransitionRule(RECOVER, Set.of(ARCHIVED), CONTENT_PUBLISH, Set.of(NO_OPEN_VERSION)),
			new TransitionRule(RETIRE, Set.of(PUBLISHED), CONTENT_RETIRE),
			new TransitionRule(RESTORE, Set.of(SUPERSEDED, RETIRED), CONTENT_PUBLISH),
			new TransitionRule(OPEN_NEW_VERSION, VersionStatus.closed(), CONTENT_WRITE, Set.of(NO_OPEN_VERSION)));

	/**
	 * Whether the caller may perform the action on the version and, if not, why - what
	 * guards the endpoint of that action.
	 */
	public TransitionDecision decide(VersionAction action, TransitionContext context) {
		return ruleOf(action).decide(context);
	}

	private static TransitionRule ruleOf(VersionAction action) {
		return RULES.stream().filter(rule -> rule.action() == action).findFirst().orElseThrow();
	}

	/**
	 * Whether an allowed action is allowed only through {@code content:admin}: on someone
	 * else's version or claim, or a review of the caller's own version (2.3).
	 */
	public boolean overrides(VersionAction action, TransitionContext context) {
		return ruleOf(action).overrides(context);
	}

	/** The actions the caller may perform on the version, in the order of the enum. */
	public Set<VersionAction> allowedActions(TransitionContext context) {
		return actionsWhere(rule -> rule.allows(context));
	}

	/** Those of {@link #allowedActions} allowed only through {@code content:admin}. */
	public Set<VersionAction> overrideActions(TransitionContext context) {
		return actionsWhere(rule -> rule.overrides(context));
	}

	private static Set<VersionAction> actionsWhere(Predicate<TransitionRule> holds) {
		return RULES.stream()
			.filter(holds)
			.map(TransitionRule::action)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(VersionAction.class)));
	}

}
