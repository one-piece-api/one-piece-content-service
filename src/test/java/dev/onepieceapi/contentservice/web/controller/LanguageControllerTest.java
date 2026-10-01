package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.service.LanguageService;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageCodeException;
import dev.onepieceapi.contentservice.service.exception.LanguageInUseException;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig} to prove the catalog's read/write split is
 * actually enforced - {@code GET} for any authenticated caller,
 * {@code POST}/{@code DELETE} only for {@code languages:manage}.
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
		when(this.service.list()).thenReturn(List.of(new Language("en", "English")));

		var request = get("/languages").with(callerWith(Permission.CONTENT_WRITE));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void anUnauthenticatedCallerIsUnauthorizedToList() throws Exception {
		this.mockMvc.perform(get("/languages")).andExpect(status().isUnauthorized());
	}

	@Test
	void aCallerWithLanguagesManageCanCreateALanguage() throws Exception {
		when(this.service.create(any(), any(), any())).thenReturn(new Language("fr", "Français"));

		var request = post("/languages").with(callerWith(Permission.LANGUAGES_MANAGE))
			.contentType("application/json")
			.content("{\"code\":\"fr\",\"name\":\"Français\"}");
		this.mockMvc.perform(request).andExpect(status().isCreated());
	}

	@Test
	void aCallerWithoutLanguagesManageIsForbiddenFromCreating() throws Exception {
		var request = post("/languages").with(callerWith(Permission.CONTENT_READ))
			.contentType("application/json")
			.content("{\"code\":\"fr\",\"name\":\"Français\"}");
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithLanguagesManageCanDeleteALanguage() throws Exception {
		var request = delete("/languages/fr").with(callerWith(Permission.LANGUAGES_MANAGE));
		this.mockMvc.perform(request).andExpect(status().isNoContent());
	}

	@Test
	void aCallerWithoutLanguagesManageIsForbiddenFromDeleting() throws Exception {
		var request = delete("/languages/fr").with(callerWith(Permission.CONTENT_READ));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aLanguageRefusedByTheRulesOfTheCatalogKeepsItsOwnErrorCode() throws Exception {
		when(this.service.create(any(), any(), any())).thenThrow(new InvalidLanguageCodeException("123"));

		var request = post("/languages").with(callerWith(Permission.LANGUAGES_MANAGE))
			.contentType("application/json")
			.content("{\"code\":\"123\",\"name\":\"Numbers\"}");
		this.mockMvc.perform(request)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_INVALID_LANGUAGE_CODE"));
	}

	@Test
	void deletingALanguageStillUsedByContentIsAConflict() throws Exception {
		doThrow(new LanguageInUseException("it")).when(this.service).delete(any(), any());

		var request = delete("/languages/it").with(callerWith(Permission.LANGUAGES_MANAGE));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_LANGUAGE_IN_USE"));
	}

}
