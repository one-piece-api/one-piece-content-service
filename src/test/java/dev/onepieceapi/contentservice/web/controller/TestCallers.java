package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import dev.onepieceapi.contentservice.web.security.ContentAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Signs a MockMvc request in as a caller holding the given permissions - the same token
 * the real JWT converter builds, without a Keycloak to issue it.
 */
final class TestCallers {

	static final String USERNAME = "crewmate";

	static final String EMAIL = "crewmate@onepiece.local";

	private TestCallers() {
	}

	static RequestPostProcessor callerWith(Permission... permissions) {
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.subject(UUID.randomUUID().toString())
			.claim("email", EMAIL)
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		var user = new User(UUID.fromString(jwt.getSubject()), USERNAME, EMAIL);
		var caller = new AuthenticatedCaller(user, Set.of(permissions));
		var authorities = Arrays.stream(permissions)
			.map(permission -> new SimpleGrantedAuthority(permission.authority()))
			.toList();
		return authentication(new ContentAuthenticationToken(jwt, caller, authorities));
	}

}
