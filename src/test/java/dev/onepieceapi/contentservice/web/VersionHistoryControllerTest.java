package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.domain.ContentVersion;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves Step 7's version history/rollback routes are gated by {@code content:publish}
 * only - unlike {@link EncyclopediaController}'s own item-keyed reads, a caller with only
 * {@code content:read} must be forbidden here (flows document 7.7).
 */
@WebMvcTest(VersionHistoryController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class VersionHistoryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DevilFruitTypeService service;

	@Test
	void aCallerWithContentPublishCanListVersionHistory() throws Exception {
		when(this.service.listVersions(any())).thenReturn(List.of());
		when(this.service.getLiveVersionId(any())).thenReturn(null);

		var request = get("/devil-fruit-types/" + UUID.randomUUID() + "/versions")
			.with(asUserWithAuthorities("PERMISSION_content:publish"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithOnlyContentReadIsForbiddenFromListingVersionHistory() throws Exception {
		var request = get("/devil-fruit-types/" + UUID.randomUUID() + "/versions")
			.with(asUserWithAuthorities("PERMISSION_content:read"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anUnauthenticatedCallerIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/devil-fruit-types/" + UUID.randomUUID() + "/versions"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aCallerWithContentPublishCanGetAVersionsFullContent() throws Exception {
		var version = new ContentVersion(UUID.randomUUID(), UUID.randomUUID(), 1, "Paramishia", UUID.randomUUID(),
				"publisher@onepiece.local", Instant.EPOCH, Map.of());
		when(this.service.getVersion(any(), any())).thenReturn(version);
		when(this.service.getLiveVersionId(any())).thenReturn(version.id());

		var request = get("/devil-fruit-types/" + version.itemId() + "/versions/" + version.id())
			.with(asUserWithAuthorities("PERMISSION_content:publish"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutContentPublishIsForbiddenFromGettingAVersion() throws Exception {
		var request = get("/devil-fruit-types/" + UUID.randomUUID() + "/versions/" + UUID.randomUUID())
			.with(asUserWithAuthorities("PERMISSION_content:read"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithContentPublishCanRestoreAVersion() throws Exception {
		var version = new ContentVersion(UUID.randomUUID(), UUID.randomUUID(), 1, "Paramishia", UUID.randomUUID(),
				"publisher@onepiece.local", Instant.EPOCH, Map.of());
		when(this.service.restore(any(), any(), any(), any())).thenReturn(version);

		var request = post("/devil-fruit-types/" + version.itemId() + "/versions/" + version.id() + "/restore")
			.with(asUserWithAuthorities("PERMISSION_content:publish"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutContentPublishIsForbiddenFromRestoring() throws Exception {
		var request = post("/devil-fruit-types/" + UUID.randomUUID() + "/versions/" + UUID.randomUUID() + "/restore")
			.with(asUserWithAuthorities("PERMISSION_content:review"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	private static RequestPostProcessor asUserWithAuthorities(String... authorities) {
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.subject(UUID.randomUUID().toString())
			.claim("email", "publisher@onepiece.local")
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
