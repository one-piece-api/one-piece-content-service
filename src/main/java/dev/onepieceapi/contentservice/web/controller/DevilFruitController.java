package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.DevilFruitService;
import dev.onepieceapi.contentservice.service.DevilFruitTypeLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitResponseMapper;
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
import java.util.UUID;

/**
 * The Devil Fruit section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * and the list narrowed to the fruits of a type. The type a fruit points to is answered
 * as it is today, looked up once for the whole page (implementation plan of the Devil
 * Fruit, D1, D4).
 */
@RestController
@RequestMapping(ApiPaths.DEVIL_FRUITS)
@Tag(name = "Devil Fruits")
class DevilFruitController extends ContentController<DevilFruit, DevilFruitResponse, DevilFruitNamesResponse> {

	private final DevilFruitService service;

	private final DevilFruitTypeLinks links;

	@Autowired
	DevilFruitController(DevilFruitService service, DevilFruitTypeLinks links) {
		super(service, access -> DevilFruitResponseMapper.toVersionResponse(access,
				links.referencesOf(DevilFruitResponseMapper.typesOf(List.of(access.version().body())))));
		this.service = service;
		this.links = links;
	}

	/**
	 * One page of the list, narrowed to the fruits of a type when asked. A sort by
	 * {@code name} reads the name in the language of the standard {@code Accept-Language}
	 * header, the one the caller is reading in.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<DevilFruitNamesResponse>> list(
			@ParameterObject @Valid DevilFruitListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(DevilFruitRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<DevilFruitNamesResponse>> summaryResponses(
			List<ContentSummary<DevilFruit>> rows, AuthenticatedCaller caller) {
		var types = this.links
			.referencesOf(DevilFruitResponseMapper.typesOf(rows.stream().map(row -> row.version().body()).toList()));
		return rows.stream().map(row -> DevilFruitResponseMapper.toSummaryResponse(row, types)).toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.CONTENT_LIST)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid DevilFruitRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		DevilFruit written = DevilFruitRequestMapper.toDomain(request);
		var content = this.service.create(caller.permissions(), caller.user(), written);
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<DevilFruitResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid DevilFruitRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		DevilFruit written = DevilFruitRequestMapper.toDomain(request);
		VersionAccess<DevilFruit> version = this.service.edit(caller.permissions(), caller.user(), id, number, written);
		return versionResponse(version);
	}

}
