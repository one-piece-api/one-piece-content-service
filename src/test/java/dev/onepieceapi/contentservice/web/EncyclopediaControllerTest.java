package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import dev.onepieceapi.contentservice.web.security.ContentAuthenticationToken;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code GET /encyclopedia} is gated by {@code content:read} - the first route in
 * this service to use that authority, so unlike claim/reject/publish (already covered by
 * {@link DevilFruitTypeControllerTest}'s cases for their own permissions) it needs its
 * own demonstration.
 */
@WebMvcTest(EncyclopediaController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class EncyclopediaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DevilFruitTypeService service;

	@Test
	void aCallerWithContentReadCanListTheEncyclopedia() throws Exception {
		when(this.service.listEncyclopedia()).thenReturn(List.of());

		var request = get("/encyclopedia").with(asUserWithAuthorities("PERMISSION_content:read"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutContentReadIsForbidden() throws Exception {
		var request = get("/encyclopedia").with(asUserWithAuthorities("PERMISSION_content:write"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anUnauthenticatedCallerIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/encyclopedia")).andExpect(status().isUnauthorized());
	}

	private static RequestPostProcessor asUserWithAuthorities(String... authorities) {
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.subject(UUID.randomUUID().toString())
			.claim("email", "editor@onepiece.local")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		var caller = new AuthenticatedCaller(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
		Set<SimpleGrantedAuthority> grantedAuthorities = Set.of(authorities)
			.stream()
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toSet());
		return authentication(new ContentAuthenticationToken(jwt, caller, grantedAuthorities));
	}

}
