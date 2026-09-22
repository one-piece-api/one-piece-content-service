package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.persistence.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionStatus;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.service.EncyclopediaEntry;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig} to prove {@code POST /devil-fruit-types} is
 * actually gated by the {@code content:write} authority, not just reachable given an
 * already-authenticated principal - same pattern as {@code one-piece-user-service}'s
 * {@code UserControllerTest}.
 */
@WebMvcTest(DevilFruitTypeController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class DevilFruitTypeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DevilFruitTypeService service;

	@Test
	void aCallerWithContentWriteCanCreateADraft() throws Exception {
		var revision = new WorkingRevisionEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"editor@onepiece.local", WorkingRevisionStatus.DRAFT, Instant.EPOCH);
		when(this.service.createDraft(any(), any())).thenReturn(revision);
		when(this.service.translationsOf(any())).thenReturn(List.of());

		var request = post("/devil-fruit-types").with(asUserWithAuthorities("PERMISSION_content:write"));
		this.mockMvc.perform(request).andExpect(status().isCreated());
	}

	@Test
	void aCallerWithoutContentWriteIsForbidden() throws Exception {
		var request = post("/devil-fruit-types").with(asUserWithAuthorities("PERMISSION_content:read"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anUnauthenticatedCallerIsUnauthorized() throws Exception {
		this.mockMvc.perform(post("/devil-fruit-types")).andExpect(status().isUnauthorized());
	}

	@Test
	void aCallerWithContentWriteCanDeleteTheirOwnDraft() throws Exception {
		var workingRevisionId = UUID.randomUUID();

		var request = delete("/devil-fruit-types/" + workingRevisionId)
			.with(asUserWithAuthorities("PERMISSION_content:write"));
		this.mockMvc.perform(request).andExpect(status().isNoContent());

		verify(this.service).deleteDraft(eq(workingRevisionId), any(), any());
	}

	@Test
	void aCallerWithoutContentWriteIsForbiddenFromDeleting() throws Exception {
		var request = delete("/devil-fruit-types/" + UUID.randomUUID())
			.with(asUserWithAuthorities("PERMISSION_content:read"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithContentReviewCanClaimAQueuedRevision() throws Exception {
		var revision = new WorkingRevisionEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"editor@onepiece.local", WorkingRevisionStatus.IN_REVIEW, Instant.EPOCH);
		when(this.service.claim(any(), any(), any())).thenReturn(revision);
		when(this.service.translationsOf(any())).thenReturn(List.of());

		var request = post("/devil-fruit-types/" + revision.getId() + "/claim")
			.with(asUserWithAuthorities("PERMISSION_content:review"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutContentReviewIsForbiddenFromClaiming() throws Exception {
		var request = post("/devil-fruit-types/" + UUID.randomUUID() + "/claim")
			.with(asUserWithAuthorities("PERMISSION_content:write"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithContentPublishCanPublishAReviewedRevision() throws Exception {
		var revision = new WorkingRevisionEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"editor@onepiece.local", WorkingRevisionStatus.PUBLISHED, Instant.EPOCH);
		when(this.service.publish(any(), any(), any())).thenReturn(revision);
		when(this.service.translationsOf(any())).thenReturn(List.of());

		var request = post("/devil-fruit-types/" + revision.getId() + "/publish")
			.with(asUserWithAuthorities("PERMISSION_content:publish"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutContentPublishIsForbiddenFromPublishing() throws Exception {
		var request = post("/devil-fruit-types/" + UUID.randomUUID() + "/publish")
			.with(asUserWithAuthorities("PERMISSION_content:review"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithContentPublishCanRetireAnItem() throws Exception {
		var itemId = UUID.randomUUID();
		var version = new ContentVersionEntity(UUID.randomUUID(), itemId, 1, "Paramishia", UUID.randomUUID(),
				"publisher@onepiece.local", Instant.EPOCH);
		when(this.service.getEncyclopediaItem(itemId))
			.thenReturn(new EncyclopediaEntry.RetiredItem(version, List.of()));

		var request = post("/devil-fruit-types/" + itemId + "/retire")
			.with(asUserWithAuthorities("PERMISSION_content:publish"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutContentPublishIsForbiddenFromRetiring() throws Exception {
		var request = post("/devil-fruit-types/" + UUID.randomUUID() + "/retire")
			.with(asUserWithAuthorities("PERMISSION_content:write"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
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
