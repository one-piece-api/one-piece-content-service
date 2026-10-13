package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.service.story.SagaLinks;
import dev.onepieceapi.contentservice.service.story.SagaService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import dev.onepieceapi.contentservice.web.dto.request.LinkableNumberedRequest;
import dev.onepieceapi.contentservice.web.dto.request.SagaRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.NumberedReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.SagaNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.SagaResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.StoryRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.StoryResponseMapper;
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
 * The saga section: every endpoint of {@link ContentController}, plus creating a content
 * and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write}, and the
 * sagas an arc may be linked to, which take the same.
 */
@RestController
@RequestMapping(ApiPaths.SAGAS)
@Tag(name = "Sagas")
class SagaController extends ContentController<Saga, SagaResponse, SagaNamesResponse> {

	private final SagaService service;

	private final SagaLinks links;

	@Autowired
	SagaController(SagaService service, SagaLinks links) {
		super(service, StoryResponseMapper::toVersionResponse);
		this.service = service;
		this.links = links;
	}

	/**
	 * One page of the list; {@code sort=number} orders it by number. A sort by
	 * {@code name} reads the title in the language of the standard
	 * {@code Accept-Language} header.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<SagaNamesResponse>> list(@ParameterObject @Valid ContentListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(ContentRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<SagaNamesResponse>> summaryResponses(List<ContentSummary<Saga>> rows,
			AuthenticatedCaller caller) {
		return rows.stream().map(StoryResponseMapper::toSummaryResponse).toList();
	}

	/**
	 * The sagas an arc may be linked to: those with a version that passed review, by
	 * number, narrowed by what their number or a title contains.
	 */
	@GetMapping(ApiPaths.CONTENT_LINKABLE)
	PageResponse<NumberedReferenceResponse> linkable(@ParameterObject @Valid LinkableNumberedRequest request) {
		var page = this.links.linkable(request.q(), request.pageOrDefault(), request.sizeOrDefault());
		return PageResponse.from(page.map(StoryResponseMapper::toResponse));
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.CONTENT_LIST)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid SagaRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var content = this.service.create(caller.permissions(), caller.user(), StoryRequestMapper.toDomain(request));
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<SagaResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid SagaRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.edit(caller.permissions(), caller.user(), id, number,
				StoryRequestMapper.toDomain(request));
		return StoryResponseMapper.toVersionResponse(version);
	}

}
