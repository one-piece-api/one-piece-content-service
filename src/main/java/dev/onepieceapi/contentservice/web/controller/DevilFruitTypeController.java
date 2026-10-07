package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitTypeRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitTypeResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Devil Fruit Type section: every endpoint of {@link ContentController}, plus
 * creating a content and editing its draft (UF-CNT-01, UF-CNT-02), which take
 * {@code content:write}.
 */
@RestController
@RequestMapping(ApiPaths.DEVIL_FRUIT_TYPES)
@Tag(name = "Devil Fruit Types")
class DevilFruitTypeController
		extends ContentController<DevilFruitType, DevilFruitTypeResponse, DevilFruitTypeNamesResponse> {

	private final DevilFruitTypeService service;

	@Autowired
	DevilFruitTypeController(DevilFruitTypeService service) {
		super(service, DevilFruitTypeResponseMapper::toVersionResponse,
				DevilFruitTypeResponseMapper::toSummaryResponse);
		this.service = service;
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.CONTENT_LIST)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid DevilFruitTypeRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		DevilFruitType written = DevilFruitTypeRequestMapper.toDomain(request);
		var content = this.service.create(caller.permissions(), caller.user(), written);
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<DevilFruitTypeResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid DevilFruitTypeRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		DevilFruitType written = DevilFruitTypeRequestMapper.toDomain(request);
		var version = this.service.edit(caller.permissions(), caller.user(), id, number, written);
		return DevilFruitTypeResponseMapper.toVersionResponse(version);
	}

}
