package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.dto.UpdateDraftRequest;
import dev.onepieceapi.contentservice.web.dto.WorkingRevisionDetailResponse;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
	WorkingRevisionDetailResponse create(@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.createDraft(caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()));
	}

	@PutMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	WorkingRevisionDetailResponse update(@PathVariable UUID workingRevisionId, @RequestBody UpdateDraftRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.updateDraft(workingRevisionId, caller.id(), caller.email(), request.romaji(),
				request.translations());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()));
	}

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	WorkingRevisionDetailResponse get(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.getOwnDraft(workingRevisionId, caller.id());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()));
	}

}
