package dev.onepieceapi.contentservice.web.security;

import dev.onepieceapi.contentservice.web.ApiPaths;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Every secured endpoint, in one place: its HTTP method, its path (from {@link ApiPaths},
 * so it can never drift from what the controller actually maps) and the rule that
 * authorizes it. {@link SecurityConfig} only ever calls {@link #configureAll}, staying
 * unaware of how the registry itself is built. Same pattern as
 * {@code one-piece-user-service}'s {@code SecuredEndpoint}
 * ({@code docs/adr/0009-permission-based-endpoint-registry.md} there).
 */
enum SecuredEndpoint {

	HEALTH(HttpMethod.GET, ApiPaths.HEALTH, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::permitAll),

	// The API contract and its Swagger UI: any signed-in user - every operation "Try it
	// out" calls is still authorized by its own entry below.
	API_DOCS(HttpMethod.GET, ApiPaths.API_DOCS, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),
	SWAGGER_UI(HttpMethod.GET, ApiPaths.SWAGGER_UI, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),
	SWAGGER_UI_ENTRY(HttpMethod.GET, ApiPaths.SWAGGER_UI_ENTRY,
			AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),

	DEVIL_FRUIT_TYPE_CREATE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPES, Permission.CONTENT_WRITE),
	DEVIL_FRUIT_TYPE_UPDATE(HttpMethod.PUT, ApiPaths.DEVIL_FRUIT_TYPE_BY_ID, Permission.CONTENT_WRITE),
	DEVIL_FRUIT_TYPE_GET(HttpMethod.GET, ApiPaths.DEVIL_FRUIT_TYPE_BY_ID, Permission.CONTENT_WRITE),
	DEVIL_FRUIT_TYPE_DELETE(HttpMethod.DELETE, ApiPaths.DEVIL_FRUIT_TYPE_BY_ID, Permission.CONTENT_WRITE),
	DEVIL_FRUIT_TYPE_SUBMIT(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_SUBMIT, Permission.CONTENT_WRITE),
	DEVIL_FRUIT_TYPE_WITHDRAW(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_WITHDRAW, Permission.CONTENT_WRITE),
	MY_DRAFTS_LIST(HttpMethod.GET, ApiPaths.MY_DRAFTS, Permission.CONTENT_WRITE),

	DEVIL_FRUIT_TYPE_CLAIM(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_CLAIM, Permission.CONTENT_REVIEW),
	DEVIL_FRUIT_TYPE_RELEASE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_RELEASE, Permission.CONTENT_REVIEW),
	DEVIL_FRUIT_TYPE_APPROVE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_APPROVE, Permission.CONTENT_REVIEW),
	DEVIL_FRUIT_TYPE_REJECT(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_REJECT, Permission.CONTENT_REVIEW),
	REVIEW_QUEUE_LIST(HttpMethod.GET, ApiPaths.REVIEW_QUEUE, Permission.CONTENT_REVIEW),
	REVIEW_QUEUE_ITEM_GET(HttpMethod.GET, ApiPaths.REVIEW_QUEUE_ITEM, Permission.CONTENT_REVIEW),

	DEVIL_FRUIT_TYPE_PUBLISH(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_PUBLISH, Permission.CONTENT_PUBLISH),
	DEVIL_FRUIT_TYPE_RETIRE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_RETIRE, Permission.CONTENT_PUBLISH),
	ENCYCLOPEDIA_LIST(HttpMethod.GET, ApiPaths.ENCYCLOPEDIA, Permission.CONTENT_READ),
	ENCYCLOPEDIA_ITEM_GET(HttpMethod.GET, ApiPaths.ENCYCLOPEDIA_ITEM, Permission.CONTENT_READ),

	DEVIL_FRUIT_TYPE_EDIT_PUBLISHED(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_EDIT_PUBLISHED,
			Permission.CONTENT_WRITE),

	DEVIL_FRUIT_TYPE_VERSIONS_LIST(HttpMethod.GET, ApiPaths.DEVIL_FRUIT_TYPE_VERSIONS, Permission.CONTENT_PUBLISH),
	DEVIL_FRUIT_TYPE_VERSION_GET(HttpMethod.GET, ApiPaths.DEVIL_FRUIT_TYPE_VERSION_BY_ID, Permission.CONTENT_PUBLISH),
	DEVIL_FRUIT_TYPE_VERSION_RESTORE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_VERSION_RESTORE,
			Permission.CONTENT_PUBLISH),

	/**
	 * Readable by any authenticated caller, not gated on a {@code content:*}/
	 * {@code languages:manage} permission: EDITOR/REVIEWER/PUBLISHER each hold a
	 * different single {@code content:*} permission, yet every one of them needs this
	 * list to render its own screen's language tabs - no single existing permission
	 * covers all three.
	 */
	LANGUAGE_LIST(HttpMethod.GET, ApiPaths.LANGUAGES, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),
	LANGUAGE_CREATE(HttpMethod.POST, ApiPaths.LANGUAGES, Permission.LANGUAGES_MANAGE),
	LANGUAGE_DELETE(HttpMethod.DELETE, ApiPaths.LANGUAGE_BY_CODE, Permission.LANGUAGES_MANAGE);

	private final HttpMethod method;

	private final String path;

	private final Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> rule;

	/**
	 * The permission this endpoint requires, or null when the rule isn't
	 * permission-based.
	 */
	private final Permission permission;

	SecuredEndpoint(HttpMethod method, String path,
			Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> rule) {
		this.method = method;
		this.path = path;
		this.rule = rule;
		this.permission = null;
	}

	SecuredEndpoint(HttpMethod method, String path, Permission permission) {
		this.method = method;
		this.path = path;
		this.rule = authorizedUrl -> authorizedUrl.hasAuthority(permission.authority());
		this.permission = permission;
	}

	/**
	 * Applies every constant's rule to the given registry - the one entry point
	 * {@link SecurityConfig} calls.
	 */
	static void configureAll(
			AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reg) {
		for (SecuredEndpoint endpoint : values()) {
			var authorizedUrl = reg.requestMatchers(endpoint.method, endpoint.path);
			endpoint.rule.accept(authorizedUrl);
		}
	}

	/**
	 * The permission required by the endpoint mapped at exactly this method and path
	 * template (as written in {@link ApiPaths}), if any - what
	 * {@link RequiredPermissionOpenApiCustomizer} documents on each OpenAPI operation, so
	 * the published contract is derived from the same registry that enforces it.
	 */
	static Optional<Permission> requiredPermission(HttpMethod method, String path) {
		return Arrays.stream(values())
			.filter(endpoint -> endpoint.method.equals(method) && endpoint.path.equals(path))
			.findFirst()
			.map(endpoint -> endpoint.permission);
	}

}
