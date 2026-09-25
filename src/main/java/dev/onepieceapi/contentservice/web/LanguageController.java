package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.domain.Language;
import dev.onepieceapi.contentservice.service.LanguageService;
import dev.onepieceapi.contentservice.web.dto.request.CreateLanguageRequest;
import dev.onepieceapi.contentservice.web.dto.response.LanguageResponse;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Step 10 (docs/implementation-plan-content.md): the ADMIN-managed language catalog - a
 * code (exactly two lowercase letters, ISO 639-1 style) plus a full display name (e.g.
 * "English"). {@code GET} is reachable by any authenticated caller (see
 * {@code SecuredEndpoint}); create and delete require {@code languages:manage}.
 */
@RestController
@Tag(name = "Languages")
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class LanguageController {

	private final LanguageService service;

	@GetMapping(ApiPaths.LANGUAGES)
	List<LanguageResponse> list() {
		return this.service.list().stream().map(LanguageController::toResponse).toList();
	}

	@PostMapping(ApiPaths.LANGUAGES)
	@ResponseStatus(HttpStatus.CREATED)
	LanguageResponse create(@RequestBody CreateLanguageRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return toResponse(this.service.create(request.code(), request.name(), caller.id(), caller.email()));
	}

	@DeleteMapping(ApiPaths.LANGUAGE_BY_CODE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String code, @AuthenticationPrincipal AuthenticatedCaller caller) {
		this.service.delete(code, caller.id(), caller.email());
	}

	private static LanguageResponse toResponse(Language language) {
		return new LanguageResponse(language.code(), language.name());
	}

}
