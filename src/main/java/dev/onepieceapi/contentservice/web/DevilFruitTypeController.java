package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.dto.response.EncyclopediaItemDetailResponse;
import dev.onepieceapi.contentservice.web.dto.request.RejectRequest;
import dev.onepieceapi.contentservice.web.dto.request.UpdateDraftRequest;
import dev.onepieceapi.contentservice.web.dto.response.WorkingRevisionDetailResponse;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PutMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	WorkingRevisionDetailResponse update(@PathVariable UUID workingRevisionId, @RequestBody UpdateDraftRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.updateDraft(workingRevisionId, caller.id(), caller.email(), request.romaji(),
				request.translations());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	WorkingRevisionDetailResponse get(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.getOwnDraft(workingRevisionId, caller.id());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@DeleteMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID workingRevisionId, @AuthenticationPrincipal AuthenticatedCaller caller) {
		this.service.deleteDraft(workingRevisionId, caller.id(), caller.email());
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_SUBMIT)
	WorkingRevisionDetailResponse submit(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.submitForReview(workingRevisionId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_WITHDRAW)
	WorkingRevisionDetailResponse withdraw(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.withdrawToDraft(workingRevisionId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_CLAIM)
	WorkingRevisionDetailResponse claim(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.claim(workingRevisionId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_RELEASE)
	WorkingRevisionDetailResponse release(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.release(workingRevisionId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_APPROVE)
	WorkingRevisionDetailResponse approve(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.approve(workingRevisionId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_REJECT)
	WorkingRevisionDetailResponse reject(@PathVariable UUID workingRevisionId, @RequestBody RejectRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.reject(workingRevisionId, caller.id(), caller.email(), request.reason());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_PUBLISH)
	WorkingRevisionDetailResponse publish(@PathVariable UUID workingRevisionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.publish(workingRevisionId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_EDIT_PUBLISHED)
	@ResponseStatus(HttpStatus.CREATED)
	WorkingRevisionDetailResponse editPublished(@PathVariable UUID itemId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var revision = this.service.editPublishedItem(itemId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.translationsOf(revision.getId()),
				this.service.everPublished(revision.getItemId()));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_RETIRE)
	EncyclopediaItemDetailResponse retire(@PathVariable UUID itemId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		this.service.retire(itemId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toEncyclopediaDetail(this.service.getEncyclopediaItem(itemId));
	}

}
