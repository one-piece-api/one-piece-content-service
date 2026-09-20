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

	/**
	 * Placeholder-only, added in Phase 0 solely to prove the permission-gated security
	 * chain end-to-end before any real domain endpoint exists - see
	 * {@code docs/implementation-plan-content.md} Phase 0's Definition of Done. Removed
	 * once Step 1's real endpoints ({@code POST /devil-fruit-types},
	 * {@code GET /my-drafts}, ...) land.
	 */
	public static final String INTERNAL_STATUS = "/_internal/status";

	private ApiPaths() {
	}

}
