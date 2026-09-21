package dev.onepieceapi.contentservice.web.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * Same shape as {@code one-piece-user-service}'s
 * {@code ApplicationUserAuthenticationToken} (same pattern, not shared code - see the two
 * services' separate repos): the principal every controller actually wants is
 * {@link AuthenticatedCaller}, not the raw {@link Jwt} - resolving it once here means
 * every controller can just declare
 * {@code @AuthenticationPrincipal AuthenticatedCaller caller} instead of repeating
 * {@code UUID.fromString(jwt.getSubject())}/{@code jwt.getClaimAsString("email")} itself.
 */
public class ContentAuthenticationToken extends AbstractAuthenticationToken {

	private final Jwt jwt;

	@Getter
	private final AuthenticatedCaller caller;

	public ContentAuthenticationToken(Jwt jwt, AuthenticatedCaller caller,
			Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.jwt = jwt;
		this.caller = caller;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return this.jwt;
	}

	@Override
	public Object getPrincipal() {
		return this.caller;
	}

}
