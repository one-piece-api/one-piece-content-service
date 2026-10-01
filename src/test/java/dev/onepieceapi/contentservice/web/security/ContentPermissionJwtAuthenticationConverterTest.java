package dev.onepieceapi.contentservice.web.security;

import dev.onepieceapi.contentservice.domain.security.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContentPermissionJwtAuthenticationConverterTest {

	private final Converter<Jwt, AbstractAuthenticationToken> converter;

	ContentPermissionJwtAuthenticationConverterTest() {
		this.converter = new ContentPermissionJwtAuthenticationConverter();
	}

	@Test
	void theCallerCarriesThisServicesPermissionsAndIgnoresTheOthers() {
		var subject = UUID.randomUUID();
		var granted = List.of("content:read", "content:review", "users:read");

		var token = this.converter.convert(jwt(subject, Map.of("onepiece-proxy", Map.of("roles", granted))));

		var caller = (AuthenticatedCaller) token.getPrincipal();
		assertThat(caller.user().id()).isEqualTo(subject);
		assertThat(caller.user().username()).isEqualTo("zoro");
		assertThat(caller.user().email()).isEqualTo("zoro@onepiece.local");
		assertThat(caller.permissions()).containsExactlyInAnyOrder(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);
		// Every granted string stays an authority: the endpoint registry matches on
		// those.
		assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("PERMISSION_content:read", "PERMISSION_content:review", "PERMISSION_users:read");
	}

	@Test
	void aTokenWithoutPermissionsGivesACallerWithNone() {
		var token = this.converter.convert(jwt(UUID.randomUUID(), Map.of()));

		assertThat(((AuthenticatedCaller) token.getPrincipal()).permissions()).isEmpty();
		assertThat(token.getAuthorities()).isEmpty();
	}

	private static Jwt jwt(UUID subject, Map<String, Object> resourceAccess) {
		return Jwt.withTokenValue("token")
			.header("alg", "none")
			.subject(subject.toString())
			.claim("preferred_username", "zoro")
			.claim("email", "zoro@onepiece.local")
			.claim("resource_access", resourceAccess)
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
	}

}
