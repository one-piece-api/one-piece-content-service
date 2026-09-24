package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.dto.response.ContentVersionDetailResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentVersionResponse;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * UF-CNT-09/12 (docs/user-flows/authentication-and-user-management.md), Step 7: version
 * history and rollback for one item - gated on {@code content:publish} only, not
 * {@code content:read} (flows document 7.7), unlike {@link EncyclopediaController}'s own
 * item-keyed reads. Lives in its own controller for the same reason
 * {@link ReviewQueueController} and {@link EncyclopediaController} do: a different
 * authorization shape from every other item-keyed route in this service, not an
 * OR-condition bolted onto one of them.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class VersionHistoryController {

	private final DevilFruitTypeService service;

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_VERSIONS)
	List<ContentVersionResponse> list(@PathVariable UUID itemId) {
		var liveVersionId = this.service.getLiveVersionId(itemId);
		return this.service.listVersions(itemId)
			.stream()
			.map(version -> DevilFruitTypeResponseMapper.toVersion(version, version.id().equals(liveVersionId)))
			.toList();
	}

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_VERSION_BY_ID)
	ContentVersionDetailResponse get(@PathVariable UUID itemId, @PathVariable UUID versionId) {
		var version = this.service.getVersion(itemId, versionId);
		var liveVersionId = this.service.getLiveVersionId(itemId);
		return DevilFruitTypeResponseMapper.toVersionDetail(version, version.id().equals(liveVersionId));
	}

	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPE_VERSION_RESTORE)
	ContentVersionResponse restore(@PathVariable UUID itemId, @PathVariable UUID versionId,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.restore(itemId, versionId, caller.id(), caller.email());
		return DevilFruitTypeResponseMapper.toVersion(version, true);
	}

}
