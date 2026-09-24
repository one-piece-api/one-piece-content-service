package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.LanguageService;
import dev.onepieceapi.contentservice.web.dto.response.LanguageResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig} to prove the catalog's read/write split is
 * actually enforced - {@code GET} for any authenticated caller,
 * {@code POST}/{@code DELETE} only for {@code languages:manage} - same pattern as
 * {@link dev.onepieceapi.contentservice.web.DevilFruitTypeControllerTest}.
 */
@WebMvcTest(LanguageController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class LanguageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LanguageService service;

	@Test
	void anyAuthenticatedCallerCanListLanguages() throws Exception {
		when(this.service.list()).thenReturn(List.of(new LanguageResponse("en", "English")));

		var request = get("/languages").with(asUserWithAuthorities("PERMISSION_content:write"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void anUnauthenticatedCallerIsUnauthorizedToList() throws Exception {
		this.mockMvc.perform(get("/languages")).andExpect(status().isUnauthorized());
	}

	@Test
	void aCallerWithLanguagesManageCanCreateALanguage() throws Exception {
		when(this.service.create(any(), any(), any(), any())).thenReturn(new LanguageResponse("fr", "Français"));

		var request = post("/languages").with(asUserWithAuthorities("PERMISSION_languages:manage"))
			.contentType("application/json")
			.content("{\"code\":\"fr\",\"name\":\"Français\"}");
		this.mockMvc.perform(request).andExpect(status().isCreated());
	}

	@Test
	void aCallerWithoutLanguagesManageIsForbiddenFromCreating() throws Exception {
		var request = post("/languages").with(asUserWithAuthorities("PERMISSION_content:read"))
			.contentType("application/json")
			.content("{\"code\":\"fr\",\"name\":\"Français\"}");
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithLanguagesManageCanDeleteALanguage() throws Exception {
		var request = delete("/languages/fr").with(asUserWithAuthorities("PERMISSION_languages:manage"));
		this.mockMvc.perform(request).andExpect(status().isNoContent());
	}

	@Test
	void aCallerWithoutLanguagesManageIsForbiddenFromDeleting() throws Exception {
		var request = delete("/languages/fr").with(asUserWithAuthorities("PERMISSION_content:read"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	private static RequestPostProcessor asUserWithAuthorities(String... authorities) {
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.subject(UUID.randomUUID().toString())
			.claim("email", "admin@onepiece.local")
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
