package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.service.DevilFruitTypeLinks;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.request.LinkableTypesRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.TypeReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitTypeRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitTypeResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import dev.onepieceapi.contentservice.web.validation.SortableBy;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Devil Fruit Type section: every endpoint of {@link ContentController}, plus
 * creating a content and editing its draft (UF-CNT-01, UF-CNT-02), which take
 * {@code content:write}, and the types a fruit may be linked to, which take the same.
 */
@RestController
@RequestMapping(ApiPaths.DEVIL_FRUIT_TYPES)
@Tag(name = "Devil Fruit Types")
class DevilFruitTypeController
		extends ContentController<DevilFruitType, DevilFruitTypeResponse, DevilFruitTypeNamesResponse> {

	private final DevilFruitTypeService service;

	private final DevilFruitTypeLinks links;

	@Autowired
	DevilFruitTypeController(DevilFruitTypeService service, DevilFruitTypeLinks links) {
		super(service, DevilFruitTypeResponseMapper::toVersionResponse);
		this.service = service;
		this.links = links;
	}

	/**
	 * One page of the list, each row with how many fruits the caller sees of it. A sort
	 * by {@code name} reads the name in the language of the standard
	 * {@code Accept-Language} header, the one the caller is reading in.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<DevilFruitTypeNamesResponse>> list(
			@ParameterObject @Valid ContentListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(ContentRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<DevilFruitTypeNamesResponse>> summaryResponses(
			List<ContentSummary<DevilFruitType>> rows, AuthenticatedCaller caller) {
		List<UUID> ids = rows.stream().map(ContentSummary::contentId).toList();
		Map<UUID, Long> counts = this.links.fruitCounts(caller.permissions(), ids);
		return rows.stream()
			.map(row -> DevilFruitTypeResponseMapper.toSummaryResponse(row, counts.getOrDefault(row.contentId(), 0L)))
			.toList();
	}

	/**
	 * The types a fruit may be linked to: those with a version that passed review, by
	 * romaji, narrowed by what their romaji or a name contains.
	 */
	@GetMapping(ApiPaths.CONTENT_LINKABLE)
	PageResponse<TypeReferenceResponse> linkable(@ParameterObject @Valid LinkableTypesRequest request) {
		var page = this.links.linkable(request.q(), request.pageOrDefault(), request.sizeOrDefault());
		return PageResponse.from(page.map(DevilFruitResponseMapper::toResponse));
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
