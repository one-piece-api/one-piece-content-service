package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.dto.UpdateDraftRequest;
import dev.onepieceapi.contentservice.web.dto.WorkingRevisionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * UF-CNT-01/02 (docs/user-flows/authentication-and-user-management.md): create and edit a
 * private Devil Fruit Type draft. Every operation here only ever touches the caller's own
 * working revisions - see {@link DevilFruitTypeService}.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class DevilFruitTypeController {

	private final DevilFruitTypeService service;

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPES)
	@ResponseStatus(HttpStatus.CREATED)
	WorkingRevisionDetailResponse create(@AuthenticationPrincipal Jwt jwt) {
		var revision = this.service.createDraft(authorId(jwt), email(jwt));
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()));
	}

	@PutMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	WorkingRevisionDetailResponse update(@PathVariable UUID workingRevisionId, @RequestBody UpdateDraftRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		var revision = this.service.updateDraft(workingRevisionId, authorId(jwt), email(jwt), request.romaji(),
				request.translations());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()));
	}

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	WorkingRevisionDetailResponse get(@PathVariable UUID workingRevisionId, @AuthenticationPrincipal Jwt jwt) {
		var revision = this.service.getOwnDraft(workingRevisionId, authorId(jwt));
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()));
	}

	private static UUID authorId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	private static String email(Jwt jwt) {
		return jwt.getClaimAsString("email");
	}

}
