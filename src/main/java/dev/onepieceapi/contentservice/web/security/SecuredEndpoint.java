package dev.onepieceapi.contentservice.web.security;

import dev.onepieceapi.contentservice.domain.security.Permission;
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
 * unaware of how the registry itself is built. The endpoints of the entity sections, the
 * same in each, are in {@link ContentEndpoint}. Same pattern as
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

	/**
	 * Readable by any authenticated caller, not gated on a single permission: every
	 * content screen renders its language tabs from this list, whichever
	 * {@code content:*} permission its user holds.
	 */
	LANGUAGE_LIST(HttpMethod.GET, ApiPaths.LANGUAGES, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),
	LANGUAGE_CREATE(HttpMethod.POST, ApiPaths.LANGUAGES, Permission.LANGUAGES_MANAGE),
	LANGUAGE_DELETE(HttpMethod.DELETE, ApiPaths.LANGUAGE_BY_CODE, Permission.LANGUAGES_MANAGE),

	// Reading content only takes content:read: which versions the caller then sees is
	// decided per status by VisibilityPolicy, inside the service.
	// The types a fruit may be linked to are chosen by whoever writes a fruit; listed
	// before
	// the endpoints of the sections, whose "a content by id" path would otherwise take
	// it.
	LINKABLE_TYPES(HttpMethod.GET, ApiPaths.DEVIL_FRUIT_TYPES_LINKABLE, Permission.CONTENT_WRITE),

	DASHBOARD(HttpMethod.GET, ApiPaths.DASHBOARD, Permission.CONTENT_READ),
	DASHBOARD_ACTIVITY(HttpMethod.GET, ApiPaths.DASHBOARD_ACTIVITY, Permission.CONTENT_READ),
	DASHBOARD_STATUS(HttpMethod.GET, ApiPaths.DASHBOARD_STATUS, Permission.CONTENT_READ),
	DASHBOARD_STATUS_AUTHORS(HttpMethod.GET, ApiPaths.DASHBOARD_STATUS_AUTHORS, Permission.CONTENT_READ);

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
		ContentEndpoint.configureAll(reg);
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
			.map(endpoint -> endpoint.permission)
			.or(() -> ContentEndpoint.requiredPermission(method, path));
	}

}
