package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.service.story.ArcService;
import dev.onepieceapi.contentservice.service.story.SagaLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.ArcListRequest;
import dev.onepieceapi.contentservice.web.dto.request.ArcRequest;
import dev.onepieceapi.contentservice.web.dto.response.ArcNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.ArcResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
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
 * The arc section: every endpoint of {@link ContentController}, plus creating a content
 * and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write}, and the
 * list narrowed to the arcs of a saga. The saga an arc points to is answered as it is
 * today, looked up once for the whole page.
 */
@RestController
@RequestMapping(ApiPaths.ARCS)
@Tag(name = "Arcs")
class ArcController extends ContentController<Arc, ArcResponse, ArcNamesResponse> {

	private final ArcService service;

	private final SagaLinks sagas;

	@Autowired
	ArcController(ArcService service, SagaLinks sagas) {
		super(service,
				access -> StoryResponseMapper.toVersionResponse(access, sagas.referencesFor(List.of(access.body()))));
		this.service = service;
		this.sagas = sagas;
	}

	/**
	 * One page of the list, narrowed to the arcs of a saga when asked;
	 * {@code sort=number} orders it by number. A sort by {@code name} reads the title in
	 * the language of the standard {@code Accept-Language} header.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<ArcNamesResponse>> list(@ParameterObject @Valid ArcListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(StoryRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<ArcNamesResponse>> summaryResponses(List<ContentSummary<Arc>> rows,
			AuthenticatedCaller caller) {
		var references = this.sagas.referencesFor(rows.stream().map(row -> row.version().body()).toList());
		return rows.stream().map(row -> StoryResponseMapper.toSummaryResponse(row, references)).toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.CONTENT_LIST)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid ArcRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var content = this.service.create(caller.permissions(), caller.user(), StoryRequestMapper.toDomain(request));
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<ArcResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid ArcRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.edit(caller.permissions(), caller.user(), id, number,
				StoryRequestMapper.toDomain(request));
		return versionResponse(version);
	}

}
