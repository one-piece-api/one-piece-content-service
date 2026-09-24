package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.domain.EncyclopediaEntry;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.dto.response.EncyclopediaItemDetailResponse;
import dev.onepieceapi.contentservice.web.dto.response.EncyclopediaItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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
	List<EncyclopediaItemResponse> list(
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return this.service.listEncyclopedia()
			.stream()
			.map(entry -> DevilFruitTypeResponseMapper.toEncyclopediaItem(entry, acceptLanguage))
			.toList();
	}

	@GetMapping(ApiPaths.ENCYCLOPEDIA_ITEM)
	EncyclopediaItemDetailResponse get(@PathVariable UUID itemId) {
		return DevilFruitTypeResponseMapper.toEncyclopediaDetail(this.service.getEncyclopediaItem(itemId));
	}

}
