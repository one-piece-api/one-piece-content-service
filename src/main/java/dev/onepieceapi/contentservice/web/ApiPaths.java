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

	/** Across entity types: the counters per status, and the caller's latest actions. */
	public static final String DASHBOARD = "/dashboard";

	public static final String DASHBOARD_ACTIVITY = DASHBOARD + "/activity";

	/** One status across entity types: its contents, one page at a time. */
	public static final String DASHBOARD_STATUS = DASHBOARD + "/statuses/{status}";

	public static final String DASHBOARD_STATUS_AUTHORS = DASHBOARD_STATUS + "/authors";

	/** Content-keyed: a content, then one of its versions by number. */
	public static final String DEVIL_FRUIT_TYPES = "/devil-fruit-types";

	public static final String DEVIL_FRUIT_TYPE_AUTHORS = DEVIL_FRUIT_TYPES + "/authors";

	public static final String DEVIL_FRUIT_TYPE_SUMMARY = DEVIL_FRUIT_TYPES + "/summary";

	public static final String DEVIL_FRUIT_TYPE_BY_ID = DEVIL_FRUIT_TYPES + "/{id}";

	/** Posting here opens a new version of the content, from one of its closed ones. */
	public static final String DEVIL_FRUIT_TYPE_VERSIONS = DEVIL_FRUIT_TYPE_BY_ID + "/versions";

	public static final String DEVIL_FRUIT_TYPE_VERSION = DEVIL_FRUIT_TYPE_VERSIONS + "/{number}";

	public static final String DEVIL_FRUIT_TYPE_VERSION_EVENTS = DEVIL_FRUIT_TYPE_VERSION + "/events";

	/**
	 * Workflow actions: one path per transition, posted with no body - except a
	 * rejection, which carries its reason.
	 */
	public static final String DEVIL_FRUIT_TYPE_VERSION_SUBMIT = DEVIL_FRUIT_TYPE_VERSION + "/submit";

	public static final String DEVIL_FRUIT_TYPE_VERSION_PULL_BACK = DEVIL_FRUIT_TYPE_VERSION + "/pull-back";

	public static final String DEVIL_FRUIT_TYPE_VERSION_CLAIM = DEVIL_FRUIT_TYPE_VERSION + "/claim";

	public static final String DEVIL_FRUIT_TYPE_VERSION_RELEASE = DEVIL_FRUIT_TYPE_VERSION + "/release";

	public static final String DEVIL_FRUIT_TYPE_VERSION_APPROVE = DEVIL_FRUIT_TYPE_VERSION + "/approve";

	public static final String DEVIL_FRUIT_TYPE_VERSION_REJECT = DEVIL_FRUIT_TYPE_VERSION + "/reject";

	public static final String DEVIL_FRUIT_TYPE_VERSION_RETURN_TO_DRAFT = DEVIL_FRUIT_TYPE_VERSION + "/return-to-draft";

	public static final String DEVIL_FRUIT_TYPE_VERSION_PUBLISH = DEVIL_FRUIT_TYPE_VERSION + "/publish";

	public static final String DEVIL_FRUIT_TYPE_VERSION_ARCHIVE = DEVIL_FRUIT_TYPE_VERSION + "/archive";

	public static final String DEVIL_FRUIT_TYPE_VERSION_RECOVER = DEVIL_FRUIT_TYPE_VERSION + "/recover";

	public static final String DEVIL_FRUIT_TYPE_VERSION_RETIRE = DEVIL_FRUIT_TYPE_VERSION + "/retire";

	public static final String DEVIL_FRUIT_TYPE_VERSION_RESTORE = DEVIL_FRUIT_TYPE_VERSION + "/restore";

	private ApiPaths() {
	}

}
