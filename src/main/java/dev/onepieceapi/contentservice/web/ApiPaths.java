package dev.onepieceapi.contentservice.web;

/**
 * Every REST path this service exposes, as {@code public static final String} constants -
 * plain constants (not enum-backed) because Java annotation attributes require
 * compile-time constant expressions. Controllers reference these in their mapping
 * annotations; {@code security.SecuredEndpoint} references the same constants to build
 * the authorization rules, so the two can never silently diverge - same pattern as
 * {@code one-piece-user-service}'s {@code ApiPaths}
 * ({@code docs/adr/0009-permission-based-endpoint-registry.md} there).
 */
public final class ApiPaths {

	public static final String HEALTH = "/actuator/health/**";

	public static final String DEVIL_FRUIT_TYPES = "/devil-fruit-types";

	public static final String DEVIL_FRUIT_TYPE_BY_ID = "/devil-fruit-types/{workingRevisionId}";

	public static final String DEVIL_FRUIT_TYPE_SUBMIT = "/devil-fruit-types/{workingRevisionId}/submit";

	public static final String DEVIL_FRUIT_TYPE_WITHDRAW = "/devil-fruit-types/{workingRevisionId}/withdraw";

	public static final String DEVIL_FRUIT_TYPE_CLAIM = "/devil-fruit-types/{workingRevisionId}/claim";

	public static final String DEVIL_FRUIT_TYPE_RELEASE = "/devil-fruit-types/{workingRevisionId}/release";

	public static final String DEVIL_FRUIT_TYPE_APPROVE = "/devil-fruit-types/{workingRevisionId}/approve";

	public static final String DEVIL_FRUIT_TYPE_REJECT = "/devil-fruit-types/{workingRevisionId}/reject";

	/**
	 * Deliberately not under {@code /devil-fruit-types}: a personal, cross-entity list in
	 * shape (docs/implementation-plan-content.md 2) even though it only queries this one
	 * entity's table today.
	 */
	public static final String MY_DRAFTS = "/my-drafts";

	/**
	 * Same cross-entity shape as {@link #MY_DRAFTS}, but every author's `IN_REVIEW` work,
	 * not just the caller's own (Step 3's "In Revisione").
	 */
	public static final String REVIEW_QUEUE = "/review-queue";

	public static final String REVIEW_QUEUE_ITEM = "/review-queue/{workingRevisionId}";

	private ApiPaths() {
	}

}
