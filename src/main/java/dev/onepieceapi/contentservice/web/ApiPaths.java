package dev.onepieceapi.contentservice.web;

import java.util.List;

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

	/**
	 * The images of versions, of every entity, by id (implementation plan of the Devil
	 * Fruit, D5).
	 */
	public static final String IMAGES = "/images";

	public static final String IMAGE_BY_ID = IMAGES + "/{id}";

	/**
	 * The entity sections, each the root of the same set of paths below - its list, a
	 * content, then one of its versions by number. Controllers map them relative to their
	 * section; {@code security.SecuredEndpoint} prefixes them with each section.
	 */
	public static final String DEVIL_FRUIT_TYPES = "/devil-fruit-types";

	public static final String DEVIL_FRUITS = "/devil-fruits";

	public static final List<String> CONTENT_SECTIONS = List.of(DEVIL_FRUIT_TYPES, DEVIL_FRUITS);

	/**
	 * The types a fruit may be linked to - an endpoint of the Devil Fruit Type section
	 * only, so it is secured on its own and not with the ones every section has.
	 */
	public static final String CONTENT_LINKABLE = "/linkable";

	public static final String DEVIL_FRUIT_TYPES_LINKABLE = DEVIL_FRUIT_TYPES + CONTENT_LINKABLE;

	/** The section itself: its list, and where a new content is posted. */
	public static final String CONTENT_LIST = "";

	public static final String CONTENT_AUTHORS = "/authors";

	public static final String CONTENT_SUMMARY = "/summary";

	public static final String CONTENT_BY_ID = "/{id}";

	/** Posting here opens a new version of the content, from one of its closed ones. */
	public static final String CONTENT_VERSIONS = CONTENT_BY_ID + "/versions";

	public static final String CONTENT_VERSION = CONTENT_VERSIONS + "/{number}";

	public static final String CONTENT_VERSION_EVENTS = CONTENT_VERSION + "/events";

	/**
	 * Workflow actions: one path per transition, posted with no body - except a
	 * rejection, which carries its reason.
	 */
	public static final String CONTENT_VERSION_SUBMIT = CONTENT_VERSION + "/submit";

	public static final String CONTENT_VERSION_PULL_BACK = CONTENT_VERSION + "/pull-back";

	public static final String CONTENT_VERSION_CLAIM = CONTENT_VERSION + "/claim";

	public static final String CONTENT_VERSION_RELEASE = CONTENT_VERSION + "/release";

	public static final String CONTENT_VERSION_APPROVE = CONTENT_VERSION + "/approve";

	public static final String CONTENT_VERSION_REJECT = CONTENT_VERSION + "/reject";

	public static final String CONTENT_VERSION_RETURN_TO_DRAFT = CONTENT_VERSION + "/return-to-draft";

	public static final String CONTENT_VERSION_PUBLISH = CONTENT_VERSION + "/publish";

	public static final String CONTENT_VERSION_ARCHIVE = CONTENT_VERSION + "/archive";

	public static final String CONTENT_VERSION_RECOVER = CONTENT_VERSION + "/recover";

	public static final String CONTENT_VERSION_RETIRE = CONTENT_VERSION + "/retire";

	public static final String CONTENT_VERSION_RESTORE = CONTENT_VERSION + "/restore";

	private ApiPaths() {
	}

}
