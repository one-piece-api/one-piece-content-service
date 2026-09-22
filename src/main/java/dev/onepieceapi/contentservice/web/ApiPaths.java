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
	 * Item-keyed, not working-revision-keyed (UF-CNT-10, Step 8): retiring clears the
	 * item's live pointer directly, with no working revision of its own involved - same
	 * reasoning as {@link #DEVIL_FRUIT_TYPE_EDIT_PUBLISHED}.
	 */
	public static final String DEVIL_FRUIT_TYPE_RETIRE = "/devil-fruit-types/{itemId}/retire";

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

	/**
	 * Item-keyed and gated on {@code content:publish} only, not {@code content:read}
	 * (Step 7, flows document 7.7) - a different authorization shape from
	 * {@link #ENCYCLOPEDIA_ITEM}, so it lives in its own controller rather than folding
	 * into that one, same reasoning as {@link #DEVIL_FRUIT_TYPE_EDIT_PUBLISHED} vs.
	 * {@link #DEVIL_FRUIT_TYPE_BY_ID}.
	 */
	public static final String DEVIL_FRUIT_TYPE_VERSIONS = "/devil-fruit-types/{itemId}/versions";

	public static final String DEVIL_FRUIT_TYPE_VERSION_RESTORE = DEVIL_FRUIT_TYPE_VERSIONS + "/{versionId}/restore";

	/**
	 * Step 10, 3.2: the ADMIN-managed language catalog. Not under any entity's own path -
	 * it is shared system configuration, not editorial content, and every content
	 * screen's language tabs read it regardless of entity type.
	 */
	public static final String LANGUAGES = "/languages";

	public static final String LANGUAGE_BY_CODE = "/languages/{code}";

	private ApiPaths() {
	}

}
