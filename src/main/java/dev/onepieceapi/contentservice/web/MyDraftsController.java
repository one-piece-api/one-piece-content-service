package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.dto.response.WorkingRevisionSummaryResponse;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A personal, cross-entity-shaped list (docs/implementation-plan-content.md 2): every
 * working revision the caller authored that is still {@code DRAFT}/{@code IN_REVIEW} -
 * once one reaches {@code REVIEWED} it is already visible via the (future) Enciclopedia's
 * {@code content:read}, so it drops out of this list at that point (Step 3+).
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class MyDraftsController {

	private final DevilFruitTypeService service;

	@GetMapping(ApiPaths.MY_DRAFTS)
	List<WorkingRevisionSummaryResponse> list(@AuthenticationPrincipal AuthenticatedCaller caller,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return this.service.listOwnDrafts(caller.id())
			.stream()
			.map(revision -> DevilFruitTypeResponseMapper.toSummary(revision,
					this.service.translationsOf(revision.getId()), acceptLanguage))
			.toList();
	}

}
