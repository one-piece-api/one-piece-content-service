package dev.onepieceapi.contentservice.domain.security;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed set of fine-grained permissions this service knows - see
 * {@code docs/user-flows/content-editorial-workflow.md} 2.2 for what each one grants, and
 * {@code one-piece-user-service}'s
 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}/
 * {@code 0012-role-permission-catalog-management.md} for how these strings are
 * provisioned as Keycloak client roles through the already-built Role &amp; Permission
 * Catalog - no {@code realm-onepiece.json} edit needed to introduce them.
 * <p>
 * A domain concept, not a web one: the endpoint registry authorizes against it, and so do
 * the editorial rules ({@code VisibilityPolicy}).
 */
@RequiredArgsConstructor
public enum Permission {

	CONTENT_READ("content:read"),

	CONTENT_WRITE("content:write"),

	CONTENT_REVIEW("content:review"),

	CONTENT_PUBLISH("content:publish"),

	CONTENT_RETIRE("content:retire"),

	/**
	 * Lifts the conditions on who the caller is - author, claimant, not the author -
	 * where the transition table allows it (2.3). Grants no action by itself.
	 */
	CONTENT_ADMIN("content:admin"),

	/**
	 * ADMIN-only (2.2/3.2): manage the supported-language catalog - distinct from the
	 * {@code content:*} family since it is system configuration, not editorial content.
	 */
	LANGUAGES_MANAGE("languages:manage");

	/**
	 * Same Spring Security convention {@code one-piece-user-service} follows for a
	 * permission authority (e.g. {@code PERMISSION_content:read}), sourced from the JWT's
	 * {@code resource_access} claim.
	 */
	public static final String AUTHORITY_PREFIX = "PERMISSION_";

	private final String value;

	/**
	 * The permission carrying this string, if this service knows it - a token also
	 * carries the permissions of other services.
	 */
	public static Optional<Permission> of(String value) {
		return Arrays.stream(values()).filter(permission -> permission.value.equals(value)).findFirst();
	}

	/** The bare permission string, as carried on the JWT and shown to API consumers. */
	public String value() {
		return this.value;
	}

	public String authority() {
		return AUTHORITY_PREFIX + this.value;
	}

}
