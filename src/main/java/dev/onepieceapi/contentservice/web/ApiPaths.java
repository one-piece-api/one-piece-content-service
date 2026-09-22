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

	public static final String DEVIL_FRUIT_TYPE_PUBLISH = "/devil-fruit-types/{workingRevisionId}/publish";

	/**
	 * Item-keyed, unlike every other path in this family (UF-CNT-08, Step 6): once
	 * published there is no single "the" working revision to address - editing starts a
	 * brand new one, seeded from the item's live content. No collision with
	 * {@link #DEVIL_FRUIT_TYPE_BY_ID}: different route shape ({@code {id}/edit} vs bare
	 * {@code {id}}), and a path variable's name is just a binding label to Spring, not
	 * part of route matching.
	 */
	public static final String DEVIL_FRUIT_TYPE_EDIT_PUBLISHED = "/devil-fruit-types/{itemId}/edit";

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

	/**
	 * Same cross-entity, item-keyed shape (Step 5's "Enciclopedia") - not under
	 * {@code /devil-fruit-types} because {@code /devil-fruit-types/{id}} is already
	 * {@link #DEVIL_FRUIT_TYPE_BY_ID}, keyed by working revision id and author-gated;
	 * this one is keyed by item id and gated on {@code content:read} instead.
	 */
	public static final String ENCYCLOPEDIA = "/encyclopedia";

	public static final String ENCYCLOPEDIA_ITEM = "/encyclopedia/{itemId}";

	private ApiPaths() {
	}

}
