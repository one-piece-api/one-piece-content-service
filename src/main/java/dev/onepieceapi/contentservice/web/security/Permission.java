package dev.onepieceapi.contentservice.web.security;

import lombok.RequiredArgsConstructor;

/**
 * The closed set of fine-grained permissions {@link SecuredEndpoint} authorizes against -
 * see {@code docs/user-flows/authentication-and-user-management.md} 2.2 for what each one
 * grants, and {@code one-piece-user-service}'s
 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}/
 * {@code 0012-role-permission-catalog-management.md} for how these strings are
 * provisioned as Keycloak client roles through the already-built Role &amp; Permission
 * Catalog - no {@code realm-onepiece.json} edit needed to introduce them.
 */
@RequiredArgsConstructor
public enum Permission {

	CONTENT_READ("content:read"),

	CONTENT_WRITE("content:write"),

	CONTENT_REVIEW("content:review"),

	CONTENT_PUBLISH("content:publish"),

	/**
	 * ADMIN-only (2.1/2.2/3.2): manage the supported-language catalog - distinct from the
	 * {@code content:*} family since it is system configuration, not editorial content.
	 */
	LANGUAGES_MANAGE("languages:manage");

	/**
	 * Same Spring Security convention {@code one-piece-user-service} follows for a
	 * permission authority (e.g. {@code PERMISSION_content:read}), sourced from the JWT's
	 * {@code resource_access} claim - see
	 * {@link ContentPermissionJwtAuthenticationConverter}.
	 */
	public static final String AUTHORITY_PREFIX = "PERMISSION_";

	private final String value;

	public String authority() {
		return AUTHORITY_PREFIX + this.value;
	}

}
