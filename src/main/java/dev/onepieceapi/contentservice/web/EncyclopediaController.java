package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.service.EncyclopediaEntry;
import dev.onepieceapi.contentservice.web.dto.EncyclopediaItemDetailResponse;
import dev.onepieceapi.contentservice.web.dto.EncyclopediaItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * UF-CNT-07+ (docs/user-flows/authentication-and-user-management.md): "Enciclopedia" -
 * every item currently `REVIEWED` (awaiting publish), `PUBLISHED`, or `RETIRED` (Step 8),
 * gated on {@code content:read} rather than ownership or claim, unlike every other
 * controller in this service. Item-keyed, not working-revision-keyed - see
 * {@link EncyclopediaEntry}.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class EncyclopediaController {

	private final DevilFruitTypeService service;

	@GetMapping(ApiPaths.ENCYCLOPEDIA)
	List<EncyclopediaItemResponse> list() {
		return this.service.listEncyclopedia().stream().map(DevilFruitTypeResponseMapper::toEncyclopediaItem).toList();
	}

	@GetMapping(ApiPaths.ENCYCLOPEDIA_ITEM)
	EncyclopediaItemDetailResponse get(@PathVariable UUID itemId) {
		return DevilFruitTypeResponseMapper.toEncyclopediaDetail(this.service.getEncyclopediaItem(itemId));
	}

}
