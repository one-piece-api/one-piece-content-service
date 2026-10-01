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

	public static final String API_DOCS = "/v3/api-docs/**";

	public static final String SWAGGER_UI = "/swagger-ui/**";

	public static final String SWAGGER_UI_ENTRY = "/swagger-ui.html";

	/**
	 * The ADMIN-managed language catalog: shared system configuration, not editorial
	 * content, so it sits outside any entity's own path.
	 */
	public static final String LANGUAGES = "/languages";

	public static final String LANGUAGE_BY_CODE = "/languages/{code}";

	private ApiPaths() {
	}

}
