package dev.onepieceapi.contentservice.web.security;

import dev.onepieceapi.contentservice.web.ApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.function.Consumer;

/**
 * Every secured endpoint, in one place: its HTTP method, its path (from {@link ApiPaths},
 * so it can never drift from what the controller actually maps) and the rule that
 * authorizes it. {@link SecurityConfig} only ever calls {@link #configureAll}, staying
 * unaware of how the registry itself is built. Same pattern as
 * {@code one-piece-user-service}'s {@code SecuredEndpoint}
 * ({@code docs/adr/0009-permission-based-endpoint-registry.md} there).
 */
@RequiredArgsConstructor
enum SecuredEndpoint {

	HEALTH(HttpMethod.GET, ApiPaths.HEALTH, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::permitAll),

	DEVIL_FRUIT_TYPE_CREATE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPES, permission(Permission.CONTENT_WRITE)),
	DEVIL_FRUIT_TYPE_UPDATE(HttpMethod.PUT, ApiPaths.DEVIL_FRUIT_TYPE_BY_ID, permission(Permission.CONTENT_WRITE)),
	DEVIL_FRUIT_TYPE_GET(HttpMethod.GET, ApiPaths.DEVIL_FRUIT_TYPE_BY_ID, permission(Permission.CONTENT_WRITE)),
	DEVIL_FRUIT_TYPE_SUBMIT(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_SUBMIT, permission(Permission.CONTENT_WRITE)),
	DEVIL_FRUIT_TYPE_WITHDRAW(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_WITHDRAW,
			permission(Permission.CONTENT_WRITE)),
	MY_DRAFTS_LIST(HttpMethod.GET, ApiPaths.MY_DRAFTS, permission(Permission.CONTENT_WRITE)),

	DEVIL_FRUIT_TYPE_CLAIM(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_CLAIM, permission(Permission.CONTENT_REVIEW)),
	DEVIL_FRUIT_TYPE_RELEASE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_RELEASE, permission(Permission.CONTENT_REVIEW)),
	DEVIL_FRUIT_TYPE_APPROVE(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_APPROVE, permission(Permission.CONTENT_REVIEW)),
	DEVIL_FRUIT_TYPE_REJECT(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_REJECT, permission(Permission.CONTENT_REVIEW)),
	REVIEW_QUEUE_LIST(HttpMethod.GET, ApiPaths.REVIEW_QUEUE, permission(Permission.CONTENT_REVIEW)),
	REVIEW_QUEUE_ITEM_GET(HttpMethod.GET, ApiPaths.REVIEW_QUEUE_ITEM, permission(Permission.CONTENT_REVIEW)),

	DEVIL_FRUIT_TYPE_PUBLISH(HttpMethod.POST, ApiPaths.DEVIL_FRUIT_TYPE_PUBLISH,
			permission(Permission.CONTENT_PUBLISH)),
	ENCYCLOPEDIA_LIST(HttpMethod.GET, ApiPaths.ENCYCLOPEDIA, permission(Permission.CONTENT_READ)),
	ENCYCLOPEDIA_ITEM_GET(HttpMethod.GET, ApiPaths.ENCYCLOPEDIA_ITEM, permission(Permission.CONTENT_READ));

	private final HttpMethod method;

	private final String path;

	private final Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> rule;

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

	private static Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> permission(
			Permission permission) {
		return authorizedUrl -> authorizedUrl.hasAuthority(permission.authority());
	}

}
