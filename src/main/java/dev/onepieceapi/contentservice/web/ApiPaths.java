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

	/**
	 * Deliberately not under {@code /devil-fruit-types}: a personal, cross-entity list in
	 * shape (docs/implementation-plan-content.md 2) even though it only queries this one
	 * entity's table today.
	 */
	public static final String MY_DRAFTS = "/my-drafts";

	private ApiPaths() {
	}

}
