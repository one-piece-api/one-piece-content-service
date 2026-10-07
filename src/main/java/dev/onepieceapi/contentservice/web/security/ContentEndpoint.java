package dev.onepieceapi.contentservice.web.security;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.web.ApiPaths;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.Arrays;
import java.util.Optional;

/**
 * The endpoints every entity section has, with the permission each one takes - applied to
 * every section of {@link ApiPaths#CONTENT_SECTIONS}, so a new entity is secured like the
 * others by being listed there. Which versions the caller then sees, and what they may do
 * with each, is decided inside the service by {@code VisibilityPolicy} and
 * {@code TransitionPolicy}. Part of {@link SecuredEndpoint}, which applies and documents
 * it.
 */
enum ContentEndpoint {

	// Reading takes content:read.
	LIST(HttpMethod.GET, ApiPaths.CONTENT_LIST, Permission.CONTENT_READ),
	AUTHORS(HttpMethod.GET, ApiPaths.CONTENT_AUTHORS, Permission.CONTENT_READ),
	SUMMARY(HttpMethod.GET, ApiPaths.CONTENT_SUMMARY, Permission.CONTENT_READ),
	GET(HttpMethod.GET, ApiPaths.CONTENT_BY_ID, Permission.CONTENT_READ),
	VERSION(HttpMethod.GET, ApiPaths.CONTENT_VERSION, Permission.CONTENT_READ),
	VERSION_EVENTS(HttpMethod.GET, ApiPaths.CONTENT_VERSION_EVENTS, Permission.CONTENT_READ),

	// Writing takes content:write; whether this caller may change this version - its
	// author, while it is a draft - is decided by TransitionPolicy.
	CREATE(HttpMethod.POST, ApiPaths.CONTENT_LIST, Permission.CONTENT_WRITE),
	VERSION_OPEN(HttpMethod.POST, ApiPaths.CONTENT_VERSIONS, Permission.CONTENT_WRITE),
	VERSION_EDIT(HttpMethod.PUT, ApiPaths.CONTENT_VERSION, Permission.CONTENT_WRITE),
	VERSION_DELETE(HttpMethod.DELETE, ApiPaths.CONTENT_VERSION, Permission.CONTENT_WRITE),
	VERSION_SUBMIT(HttpMethod.POST, ApiPaths.CONTENT_VERSION_SUBMIT, Permission.CONTENT_WRITE),
	VERSION_PULL_BACK(HttpMethod.POST, ApiPaths.CONTENT_VERSION_PULL_BACK, Permission.CONTENT_WRITE),
	VERSION_RETURN_TO_DRAFT(HttpMethod.POST, ApiPaths.CONTENT_VERSION_RETURN_TO_DRAFT, Permission.CONTENT_WRITE),

	// Reviewing takes content:review; whether this caller may take or let go this version
	// - not its author, not held by someone else - is decided by TransitionPolicy.
	VERSION_CLAIM(HttpMethod.POST, ApiPaths.CONTENT_VERSION_CLAIM, Permission.CONTENT_REVIEW),
	VERSION_RELEASE(HttpMethod.POST, ApiPaths.CONTENT_VERSION_RELEASE, Permission.CONTENT_REVIEW),
	VERSION_APPROVE(HttpMethod.POST, ApiPaths.CONTENT_VERSION_APPROVE, Permission.CONTENT_REVIEW),
	VERSION_REJECT(HttpMethod.POST, ApiPaths.CONTENT_VERSION_REJECT, Permission.CONTENT_REVIEW),

	// Publishing, archiving, recovering and restoring take content:publish, retiring
	// content:retire; whether this version is in the right status for it - and, to
	// recover, its content has no open version - is decided by TransitionPolicy.
	VERSION_PUBLISH(HttpMethod.POST, ApiPaths.CONTENT_VERSION_PUBLISH, Permission.CONTENT_PUBLISH),
	VERSION_ARCHIVE(HttpMethod.POST, ApiPaths.CONTENT_VERSION_ARCHIVE, Permission.CONTENT_PUBLISH),
	VERSION_RECOVER(HttpMethod.POST, ApiPaths.CONTENT_VERSION_RECOVER, Permission.CONTENT_PUBLISH),
	VERSION_RETIRE(HttpMethod.POST, ApiPaths.CONTENT_VERSION_RETIRE, Permission.CONTENT_RETIRE),
	VERSION_RESTORE(HttpMethod.POST, ApiPaths.CONTENT_VERSION_RESTORE, Permission.CONTENT_PUBLISH);

	private final HttpMethod method;

	/** Relative to the section. */
	private final String path;

	private final Permission permission;

	ContentEndpoint(HttpMethod method, String path, Permission permission) {
		this.method = method;
		this.path = path;
		this.permission = permission;
	}

	/** Every endpoint of every section, with its permission. */
	static void configureAll(
			AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reg) {
		for (String section : ApiPaths.CONTENT_SECTIONS) {
			for (ContentEndpoint endpoint : values()) {
				reg.requestMatchers(endpoint.method, section + endpoint.path)
					.hasAuthority(endpoint.permission.authority());
			}
		}
	}

	/** The permission of the endpoint of some section at exactly this method and path. */
	static Optional<Permission> requiredPermission(HttpMethod method, String path) {
		return ApiPaths.CONTENT_SECTIONS.stream()
			.flatMap(section -> Arrays.stream(values())
				.filter(endpoint -> endpoint.method.equals(method) && path.equals(section + endpoint.path)))
			.findFirst()
			.map(endpoint -> endpoint.permission);
	}

}
