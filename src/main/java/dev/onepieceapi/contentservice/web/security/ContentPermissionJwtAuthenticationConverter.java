package dev.onepieceapi.contentservice.web.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves a validated JWT to an {@link AuthenticatedCaller} plus its permission
 * authorities - unlike {@code one-piece-user-service}'s
 * {@code ApplicationUserJwtAuthenticationConverter}, this service has no local/resolved
 * domain user to carry (no local persistence of identity, per the identity-management
 * document's own stance, which this service also follows) and never checks a role name
 * directly, only permissions (see {@code SecuredEndpoint}) - so
 * {@link AuthenticatedCaller} is deliberately just an id and an email, not a full
 * {@code User}.
 * <p>
 * Permissions are read from {@code resource_access.onepiece-proxy.roles} - Keycloak
 * expands a role's composite client-roles into this claim automatically (see
 * {@code one-piece-user-service}'s
 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}), so no extra lookup
 * is needed here either.
 */
@Component
class ContentPermissionJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String EMAIL_CLAIM = "email";

	private static final String RESOURCE_ACCESS_CLAIM = "resource_access";

	private static final String ROLES_CLAIM = "roles";

	/** The Keycloak client whose roles this application treats as permissions. */
	private static final String PERMISSIONS_CLIENT_ID = "onepiece-proxy";

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		var caller = new AuthenticatedCaller(UUID.fromString(jwt.getClaimAsString(JwtClaimNames.SUB)),
				jwt.getClaimAsString(EMAIL_CLAIM));
		var permissions = JwtUtils.getNestedStringListClaim(jwt, RESOURCE_ACCESS_CLAIM, PERMISSIONS_CLIENT_ID,
				ROLES_CLAIM);
		var authorities = permissions.stream()
			.map(permission -> new SimpleGrantedAuthority(Permission.AUTHORITY_PREFIX + permission))
			.toList();
		return new ContentAuthenticationToken(jwt, caller, authorities);
	}

}
